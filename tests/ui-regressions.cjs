// Browser tests exercise the shipped assets with a simulated Android bridge.
// They do not represent a test of Xiaomi system permissions or device audio.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const { chromium } = require('playwright');

const assets = path.resolve(process.env.HAZEL_TEST_ASSETS || path.join(__dirname, '../app/src/main/assets'));
const legacy = process.argv.includes('--legacy');
const output = process.env.HAZEL_TEST_OUTPUT;
const results = [], errors = [];
const server = http.createServer((req, res) => {
    const name = new URL(req.url, 'http://localhost').pathname.slice(1) || 'index.html';
    // The app serves anchor art from its own /avatar/ route; the real 1x1 PNG here is what lets
    // the scenarios assert that images actually render instead of degrading to placeholders.
    if (name.startsWith('avatar/') || name.startsWith('schedule/') || name === 'background/current') {
        res.setHeader('Content-Type', 'image/png');
        res.end(Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==', 'base64'));
        return;
    }
    if (!['index.html', 'alarm.html', 'app.js', 'style.css', 'hazel.png'].includes(name)) {
        res.writeHead(404); res.end(); return;
    }
    res.setHeader('Content-Type', ({ html: 'text/html', js: 'application/javascript', css: 'text/css', png: 'image/png' })[name.split('.').pop()]);
    res.end(fs.readFileSync(path.join(assets, name)));
});

function installMock() {
    const clone = x => JSON.parse(JSON.stringify(x));
    const state = {
        config: { soundWithoutNotifications: false, allDay: true, timezone: 'device', catchUp: false, pollSeconds: 30,
            reliable: true, boot: true, ringtone: 'starlight', customName: '未选择', volume: 85, aiOcr: true, aiKey: 'sk-test', aiModel: 'deepseek-flash', preStream: true, ringQueue: false,
            seedColor: '', amoled: false, hideRecents: false, recovery: true, turbo: true, backgroundDim: 40, cardOpacity: 94,
            ramp: true, vibrate: true, duration: 60, snoozeMinutes: 5, quietCalls: true, theme: 'light',
            windows: [{ id: 'night', name: '凌晨守候', start: 60, end: 360, days: 127, enabled: true }] },
        enabled: false, running: false, ringing: false, snapshot: {}, networkError: '', serviceError: '', startError: '',
        anchors: [
            { id: 'hazel', name: '灰泽满 Hazel', uid: 1298779265, room: 1713546334, enabled: true, avatar: true, alarm: true,
                schedule: [{ id: 'w1', days: 16, start: 1200, end: 1260, note: '游戏' }], scheduleImage: false, stats: { count7: 3, count30: 11, last: Date.now() - 3600000, lastMinutes: 95 },
                snapshot: { status: 1, start: 1700000000000, checkedAt: Date.now(), title: '画画中' } },
            { id: 'other', name: '另一位主播', uid: 111222, room: 333444, enabled: false, avatar: false, alarm: true, schedule: [], scheduleImage: false, snapshot: {} }
        ],
        inside: true, zone: 'Asia/Shanghai', deviceZone: 'Asia/Shanghai', version: '1.2.1',
        watchNotification: {status:'stopped',serviceRunning:false,registered:false,channelImportance:2},
        preview: false, now: Date.now(), snoozeAt: 0, testAt: 0, serviceHeartbeatAt: Date.now(), backgroundName: '', backgroundSet: false, recoveryAt: Date.now(),
        permissions: { notifications: false, notificationRuntime: false, notificationAppEnabled: false,
            notificationMismatch: false, notificationPolicy: 'not_revoked', alarmChannel: true, alarmChannelImportance: 4, battery: true,
            overlay: false, fullScreen: true, exact: true, dnd: false, powerSave: false, accessibility: true,
            accessibilityConnected: true, alarmVolume: 14, alarmMax: 15 }
    };
    const mock = window.__mock = { state, pending: 0, saves: 0, reads: 0, pushBeforeSaveReply: true };
    function reply(id, value, ok = true) {
        mock.pending--;
        window.NativeReply(id, ok ? { ok, value: clone(value) } : { ok, error: value });
    }
    window.HazelNative = { request(id, action, data) {
        mock.pending++;
        (mock.actions || (mock.actions = [])).push(action);
        const patch = JSON.parse(data);
        if (action === 'state') {
            mock.reads++;
            if (mock.failState) { mock.failState = false; setTimeout(() => reply(id, '模拟读取失败', false), 25); return; }
            const captured = clone(state);
            if (mock.holdNextState) {
                mock.holdNextState = false;
                mock.releaseState = () => { delete mock.releaseState; reply(id, captured); };
            } else setTimeout(() => reply(id, captured), 25);
            return;
        }
        if (action === 'save') {
            if (mock.failSave) { mock.failSave = false; setTimeout(() => reply(id, '模拟保存失败', false), 0); return; }
            Object.assign(state.config, patch);
            if (patch.timezone) state.zone = patch.timezone === 'device' ? state.deviceZone : patch.timezone;
            const captured = clone(state.config);
            mock.saves++;
            setTimeout(() => {
                // This is the original native push-before-reply order that lost repaint requests.
                if (mock.pushBeforeSaveReply) window.refreshNative();
                reply(id, captured);
            }, 0);
            return;
        }
        if (action === 'zones') {
            setTimeout(() => reply(id, [
                { id: 'Asia/Shanghai', offset: '+08:00', time: '22:00' },
                { id: 'Asia/Tokyo', offset: '+09:00', time: '23:00' },
                { id: 'America/New_York', offset: '-04:00', time: '10:00' }
            ]), 0); return;
        }
        if (action === 'saveAnchor') {
            const incoming = patch.anchor;
            if (!incoming.id) incoming.id = 'anchor-' + (mock.anchorSeq = (mock.anchorSeq || 0) + 1);
            if (incoming.alarm === undefined) incoming.alarm = true;
            const at = state.anchors.findIndex(x => x.id === incoming.id);
            if (at < 0) state.anchors.push({ ...incoming, avatar: false, schedule: [], scheduleImage: false, snapshot: {} });
            else state.anchors[at] = { ...state.anchors[at], ...incoming };
            mock.lastAnchor = incoming;
            setTimeout(() => reply(id, state.anchors), 0); return;
        }
        if (action === 'saveSchedule') {
            mock.lastSchedule = patch;
            const a = state.anchors.find(x => x.id === patch.id);
            if (a) a.schedule = clone(patch.entries); else state.schedules[patch.id] = clone(patch.entries);
            setTimeout(() => reply(id, true), 0); return;
        }
        if (action === 'saveScheduleText') { mock.lastScheduleText = patch; setTimeout(() => reply(id, true), 0); return; }
        if (action === 'getSchedule') {
            const a = state.anchors.find(x => x.id === patch.id);
            setTimeout(() => reply(id, { entries: a ? clone(a.schedule || []) : [], text: mock.scheduleTexts && mock.scheduleTexts[patch.id] || '', hasImage: !!(a && a.scheduleImage) }), 0); return;
        }
        if (action === 'pickScheduleImage') { mock.lastPickImage = patch.id; setTimeout(() => reply(id, true), 0); return; }
        if (action === 'removeScheduleImage') { mock.lastRemoveImage = patch.id; setTimeout(() => reply(id, true), 0); return; }
        if (action === 'ocrScheduleImage') { mock.lastOcr = patch;
            const now = new Date();
            const md = (now.getMonth()+1)+'/'+now.getDate();
            setTimeout(() => reply(id, '4|20:00|22:30||AI游戏'+String.fromCharCode(10)+'5|21:00|||AI杂谈'+String.fromCharCode(10)+'|19:00||'+md+'|生日会'), 0); return; }
        if (action === 'pickBackground') { mock.pickedBackground = true; setTimeout(() => reply(id, true), 0); return; }
        if (action === 'removeBackground') { mock.removedBackground = true; state.backgroundSet = false; state.backgroundName = ''; setTimeout(() => reply(id, true), 0); return; }
        if (action === 'refreshAvatars') { mock.refreshedAvatars = true; setTimeout(() => reply(id, state.anchors.length), 0); return; }
        if (action === 'deleteAnchor') {
            mock.lastDeleted = patch.id;
            state.anchors = state.anchors.filter(x => x.id !== patch.id);
            setTimeout(() => reply(id, state.anchors), 0); return;
        }
        if (action === 'resolveAnchor') {
            mock.lastResolve = patch;
            if (mock.resolveError) { const reason = mock.resolveError; setTimeout(() => reply(id, reason, false), 0); return; }
            if (String(patch.room) === '40404') { setTimeout(() => reply(id, '没有找到这个直播间，请检查房间号', false), 0); return; }
            // A uid-only request has no room yet: the bridge is what maps it, so the mock
            // answers with the room that the uid belongs to, exactly like the real one.
            const room = Number(patch.room) || 1713546334;
            setTimeout(() => reply(id, { id: patch.id || '', room, uid: Number(patch.uid) || 999999, name: '识别到的主播', avatar: true }), 0); return;
        }
        if (action === 'toggle') {
            state.enabled=patch.enabled;state.running=patch.enabled;
            setTimeout(() => reply(id, state), 0); return;
        }
        if (action === 'test') { state.ringing=true;state.alarmTest=true;state.alarmUntil=Date.now()+60000; }
        if (action === 'testLater') state.testAt=Date.now()+15000;
        if (action === 'dismiss') state.ringing=false;
        if (action === 'cancelTest') state.testAt=0;
        if (action === 'permission') mock.lastPermission = patch.kind;
        if (action === 'exportNotificationReport') mock.lastReportOptions = patch;
        if (action === 'notificationProbe') {
            mock.probes = (mock.probes || 0) + 1;
            setTimeout(() => reply(id, state.permissions.notifications ? true : '系统尚未允许通知，请先完成通知授权', state.permissions.notifications), 0); return;
        }
        setTimeout(() => reply(id, action === 'history' ? [] : true), 0);
    } };
}

(async () => {
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    const browser = await chromium.launch({ headless: true,
        ...(process.env.HAZEL_TEST_CHROMIUM ? { executablePath: process.env.HAZEL_TEST_CHROMIUM } : {}),
        args: ['--no-sandbox'] });
    const context = await browser.newContext({ viewport: { width: 393, height: 852 }, deviceScaleFactor: 1 });
    await context.addInitScript(installMock);
    const page = await context.newPage();
    page.on('pageerror', e => errors.push(e.message));
    const settle = async () => {
        await page.waitForFunction(() => window.__mock.pending === 0);
    };
    const reset = async route => {
        await page.goto(`http://127.0.0.1:${server.address().port}/`);
        await page.waitForSelector('[data-route="sound"]');
        await settle();
        if (route) await page.locator(`[data-route="${route}"]`).last().click();
    };
    const selected = async tone => page.locator(`[data-tone="${tone}"]`).evaluate(el => el.classList.contains('selected'));
    const test = async (name, fn) => {
        await fn(); results.push(name); console.log('PASS:', name);
    };
    try {
        await test(legacy ? 'original: save persists but ringtone stays unselected' : 'ringtone selection paints without leaving the sound page', async () => {
            await reset('sound');
            await page.locator('[data-tone="morning"]').click(); await settle();
            assert.equal(await page.evaluate(() => __mock.state.config.ringtone), 'morning');
            assert.equal(await selected('morning'), !legacy);
        });
        await test(legacy ? 'original: permission change does not repaint settings' : 'permission-only change repaints without config changes', async () => {
            await reset('settings');
            await page.evaluate(async () => {
                Object.assign(__mock.state.permissions, { notifications: true, notificationRuntime: true, notificationAppEnabled: true });
                await window.refreshNative();
            });
            assert.equal((await page.locator('[data-permission="notifications"] .badge').innerText()).trim(), legacy ? '去设置' : '已就绪');
        });
        if (!legacy) {
            await test('stale in-flight state cannot undo a committed ringtone', async () => {
                await reset('sound');
                await page.evaluate(() => { __mock.holdNextState = true; window.refreshNative(); });
                await page.waitForFunction(() => !!__mock.releaseState);
                await page.locator('[data-tone="morning"]').click();
                await page.waitForFunction(() => document.querySelector('[data-tone="morning"]').classList.contains('selected'));
                await page.evaluate(() => __mock.releaseState()); await settle();
                assert.equal(await selected('morning'), true);
            });
            await test('forced refresh is queued and its promise waits for completion', async () => {
                await reset('settings');
                await page.evaluate(() => {
                    __mock.holdNextState = true; window.refreshNative();
                    Object.assign(__mock.state.permissions, { notifications: true, notificationRuntime: true, notificationAppEnabled: true });
                    window.refreshNative(true).then(() => { __mock.forceCompleted = true; });
                });
                assert.equal(await page.evaluate(() => !!__mock.forceCompleted), false);
                await page.evaluate(() => __mock.releaseState());
                await page.waitForFunction(() => __mock.forceCompleted);
                assert.equal((await page.locator('[data-permission="notifications"] .badge').innerText()).trim(), '已就绪');
            });
            await test('rapid ringtone selections retain the last choice and another setting', async () => {
                await reset('sound');
                await page.evaluate(() => {
                    for (const tone of ['morning', 'urgent', 'system']) document.querySelector(`[data-tone="${tone}"]`).click();
                    const input = document.querySelector('#volume'); input.value = '63'; input.dispatchEvent(new Event('change', { bubbles: true }));
                }); await settle();
                assert.equal(await selected('system'), true);
                assert.equal(await page.locator('#volume').inputValue(), '63');
                assert.equal(await page.locator('.tone.selected').count(), 1);
            });
            await test('switch changes paint their saved state in the same page', async () => {
                await reset('sound');
                await page.locator('[data-toggle="ramp"]').click(); await settle();
                assert.equal(await page.locator('[data-toggle="ramp"]').getAttribute('aria-checked'), 'false');
                await page.locator('[data-toggle="ramp"]').click(); await settle();
                assert.equal(await page.locator('[data-toggle="ramp"]').getAttribute('aria-checked'), 'true');
            });
            await test('failed save retains the prior selection and reports the error', async () => {
                await reset('sound');
                await page.evaluate(() => { __mock.failSave = true; });
                await page.locator('[data-tone="urgent"]').click(); await settle();
                assert.equal(await selected('starlight'), true);
                assert.match(await page.locator('#toast').innerText(), /模拟保存失败/);
            });
            await test('native save without an extra push also paints immediately', async () => {
                await reset('sound');
                await page.evaluate(() => { __mock.pushBeforeSaveReply = false; });
                await page.locator('[data-tone="morning"]').click(); await settle();
                assert.equal(await selected('morning'), true);
            });
            await test('mismatched and revoked permissions remain visibly not ready', async () => {
                await reset('settings');
                for (const [runtime, app] of [[true, false], [false, true], [true, true], [false, false]]) {
                    await page.evaluate(async ([runtime, app]) => {
                        Object.assign(__mock.state.permissions, { notificationRuntime: runtime, notificationAppEnabled: app,
                            notificationMismatch: runtime !== app, notifications: runtime && app });
                        await window.refreshNative();
                    }, [runtime, app]);
                    assert.equal((await page.locator('[data-permission="notifications"] .badge').innerText()).trim(), runtime && app ? '已就绪' : '去设置');
                }
            });
            await test('separate system-notification-settings action remains accessible', async () => {
                await page.locator('[data-permission="notificationSettings"]').click(); await settle();
                assert.equal(await page.evaluate(() => __mock.lastPermission), 'notificationSettings');
            });
            await test('a failed state read does not permanently lock the refresh queue', async () => {
                await page.evaluate(async () => { __mock.failState = true; await window.refreshNative(true); });
                await page.evaluate(async () => {
                    Object.assign(__mock.state.permissions, { notifications: true, notificationRuntime: true, notificationAppEnabled: true });
                    await window.refreshNative(true);
                });
                assert.equal((await page.locator('[data-permission="notifications"] .badge').innerText()).trim(), '已就绪');
            });
            await test('notification help follows real permission changes and offers both settings routes', async () => {
                await reset('settings');
                await page.evaluate(() => { __mock.state.xiaomi = true; });
                await page.locator('[data-action="notificationHelp"]').click(); await settle();
                assert.match(await page.locator('[data-notification-help]').innerText(), /尚未允许/);
                assert.match(await page.locator('#modal').innerText(), /小米/);
                await page.locator('#modal [data-permission="standardNotifications"]').click(); await settle();
                assert.equal(await page.evaluate(() => __mock.lastPermission), 'standardNotifications');
                await page.locator('#modal [data-permission="appPermissions"]').click(); await settle();
                assert.equal(await page.evaluate(() => __mock.lastPermission), 'appPermissions');
                await page.evaluate(async () => {
                    Object.assign(__mock.state.permissions, {notifications:true,notificationRuntime:true,notificationAppEnabled:true});
                    await window.refreshNative(true);
                });
                assert.match(await page.locator('[data-notification-help]').innerText(), /系统已允许发送通知/);
                await page.locator('#modal [data-action="notificationProbe"]').click(); await settle();
                assert.equal(await page.evaluate(() => __mock.probes), 1);
                await page.evaluate(async () => {
                    Object.assign(__mock.state.permissions, {notifications:false,notificationRuntime:false,notificationAppEnabled:false});
                    await window.refreshNative(true);
                });
                assert.match(await page.locator('[data-notification-help]').innerText(), /尚未允许/);
                assert.equal(await page.locator('#modal [data-action="notificationProbe"]').isDisabled(), true);
                assert.equal(await page.evaluate(() => __mock.probes), 1);
                await page.locator('#modal [data-action="closeModal"]').click();
            });
            await test('custom schedule selection and timezone update stay visible', async () => {
                await reset('schedule');
                await page.locator('[data-all-day="false"]').click(); await settle();
                assert.equal(await page.evaluate(() => __mock.state.config.allDay), false);
                await page.locator('[data-action="chooseTimezone"]').click();
                await page.locator('[data-zone="Asia/Tokyo"]').click(); await settle();
                assert.equal(await page.evaluate(() => __mock.state.config.timezone), 'Asia/Tokyo');
                assert.match(await page.locator('#content').innerText(), /Asia\/Tokyo/);
            });
            await test('editing a time window completes without route switching', async () => {
                await page.locator('[data-edit-rule="night"]').click();
                await page.locator('#rule-name').fill('凌晨守候修复验证');
                await page.locator('#rule-start').fill('01:30');
                await page.locator('[data-action="saveRule"]').click(); await settle();
                await page.waitForFunction(() => document.querySelector('#modal').hidden);
                assert.match(await page.locator('#content').innerText(), /凌晨守候修复验证/);
                assert.match(await page.locator('#content').innerText(), /01:30/);
            });
            await test('theme selection paints without leaving settings', async () => {
                await page.locator('[data-route="settings"]').click();
                await page.locator('[data-setting="theme"]').selectOption('dark'); await settle();
                assert.equal(await page.locator('body').evaluate(el => el.classList.contains('dark')), true);
                await page.locator('[data-setting="theme"]').selectOption('light'); await settle();
            });
            await test('the schedule page exposes the weekly pre-stream reminder and it persists', async () => {
                await reset('schedule');
                const copy = await page.locator('#content').innerText();
                // The page promises a five minute heads-up; the wording must name that window.
                assert.match(copy, /按周表预告提醒/);
                assert.match(copy, /开播前 5 分钟/);
                assert.match(copy, /不响铃/);
                assert.equal(await page.locator('[data-toggle="preStream"]').getAttribute('aria-checked'), 'true');
                await page.locator('[data-toggle="preStream"]').click(); await settle();
                assert.equal(await page.evaluate(() => __mock.state.config.preStream), false);
                await page.locator('[data-toggle="preStream"]').click(); await settle();
                assert.equal(await page.evaluate(() => __mock.state.config.preStream), true);
            });
            await test('app name is Manqu and denied permission requires explicit sound consent', async () => {
                await reset('home');
                assert.equal(await page.locator('.brand-name').innerText(), 'VR闹钟');
                await page.locator('[data-action="start"]').click(); await settle();
                assert.match(await page.locator('#modal').innerText(), /通知栏和锁屏提醒可能不显示/);
                assert.equal(await page.evaluate(() => __mock.saves), 0);
                assert.equal(await page.evaluate(() => __mock.state.enabled), false);
                await page.locator('#modal [data-action="closeModal"]').click(); await settle();
                assert.equal(await page.evaluate(() => __mock.state.config.soundWithoutNotifications), false);
                assert.equal(await page.evaluate(() => __mock.actions.includes('toggle')), false);
            });
            await test('consenting starts the requested watch while permission remains denied', async () => {
                await page.locator('[data-action="start"]').click();
                await page.locator('[data-action="confirmCompatibility"]').click(); await settle();
                assert.equal(await page.evaluate(() => __mock.state.enabled), true);
                assert.equal(await page.evaluate(() => __mock.state.config.soundWithoutNotifications), true);
                assert.equal(await page.evaluate(() => __mock.state.permissions.notifications), false);
                await page.locator('[data-route="settings"]').last().click();
                assert.equal(await page.locator('[data-toggle="soundWithoutNotifications"]').getAttribute('aria-checked'), 'true');
                assert.equal(await page.locator('[data-permission="notifications"] .badge').innerText(), '去设置');
            });
            await test('failed consent save cannot start monitoring and can be retried', async () => {
                await reset('home');
                await page.locator('[data-action="start"]').click();
                await page.evaluate(() => { __mock.failSave=true; });
                await page.locator('[data-action="confirmCompatibility"]').click(); await settle();
                assert.equal(await page.evaluate(() => __mock.state.enabled), false);
                assert.equal(await page.evaluate(() => __mock.state.config.soundWithoutNotifications), false);
                assert.equal(await page.locator('#modal').isVisible(), true);
                assert.match(await page.locator('#toast').innerText(), /模拟保存失败/);
                await page.locator('[data-action="confirmCompatibility"]').click(); await settle();
                assert.equal(await page.evaluate(() => __mock.state.enabled), true);
            });
            await test('normal grant starts monitoring without enabling compatibility', async () => {
                await reset('home');
                await page.evaluate(async () => {
                    Object.assign(__mock.state.permissions, { notifications:true,notificationRuntime:true,notificationAppEnabled:true });
                    await window.refreshNative(true);
                });
                await page.locator('[data-action="start"]').click(); await settle();
                assert.equal(await page.evaluate(() => __mock.state.enabled), true);
                assert.equal(await page.evaluate(() => __mock.state.config.soundWithoutNotifications), false);
                assert.equal(await page.locator('#modal').isVisible(), false);
            });
            await test('denied immediate test resumes after consent and has an immediate stop', async () => {
                await reset('home');
                await page.locator('[data-action="testDialog"]').click();
                await page.locator('[data-action="test"]').click(); await settle();
                assert.equal(await page.evaluate(() => __mock.actions.includes('test')), false);
                await page.locator('[data-action="confirmCompatibility"]').click(); await settle();
                assert.equal(await page.evaluate(() => __mock.state.ringing), true);
                await page.locator('[data-action="dismiss"]').click(); await settle();
                assert.equal(await page.evaluate(() => __mock.state.ringing), false);
            });
            await test('denied delayed test resumes after consent and can be cancelled', async () => {
                await reset('home');
                await page.locator('[data-action="testDialog"]').click();
                await page.locator('[data-action="testLater"]').click(); await settle();
                assert.equal(await page.evaluate(() => __mock.state.testAt), 0);
                await page.locator('[data-action="confirmCompatibility"]').click(); await settle();
                assert.ok(await page.evaluate(() => __mock.state.testAt>Date.now()));
                await page.locator('[data-action="cancelTest"]').click(); await settle();
                assert.equal(await page.evaluate(() => __mock.state.testAt), 0);
            });
            await test('revocation keeps consented tests available; opt-out restores the block', async () => {
                await reset('settings');
                await page.locator('[data-toggle="soundWithoutNotifications"]').click();
                assert.equal(await page.evaluate(() => __mock.saves), 0);
                await page.locator('[data-action="confirmCompatibility"]').click(); await settle();
                await page.evaluate(async () => {
                    Object.assign(__mock.state.permissions, { notifications:true,notificationRuntime:true,notificationAppEnabled:true });
                    await window.refreshNative(true);
                    Object.assign(__mock.state.permissions, { notifications:false,notificationRuntime:false,notificationAppEnabled:false });
                    await window.refreshNative(true);
                });
                await page.locator('[data-action="testDialog"]').click();
                await page.locator('[data-action="test"]').click(); await settle();
                assert.equal(await page.evaluate(() => __mock.state.ringing), true);
                await page.evaluate(async () => { __mock.state.ringing=false;await window.refreshNative(true); });
                await page.locator('[data-toggle="soundWithoutNotifications"]').click(); await settle();
                assert.equal(await page.locator('[data-toggle="soundWithoutNotifications"]').getAttribute('aria-checked'), 'false');
                await page.locator('[data-action="testDialog"]').click();
                await page.locator('[data-action="test"]').click(); await settle();
                assert.equal(await page.evaluate(() => __mock.state.ringing), false);
                assert.equal(await page.locator('[data-action="confirmCompatibility"]').isVisible(), true);
                await page.locator('#modal [data-action="closeModal"]').click();
            });
            await test('overlay is optional and its settings route does not change notification grants', async () => {
                await page.locator('[data-permission="overlay"]').click(); await settle();
                assert.equal(await page.evaluate(() => __mock.lastPermission), 'overlay');
                assert.equal(await page.evaluate(() => __mock.state.permissions.notifications), false);
            });
            await test('policy denial is distinct and does not reopen an ineffective runtime request', async () => {
                await reset('settings');
                await page.evaluate(async () => { __mock.state.permissions.notificationPolicy='revoked'; await window.refreshNative(true); });
                assert.equal(await page.locator('[data-permission="notifications"] .badge').innerText(), '策略限制');
                await page.locator('[data-permission="notifications"]').click(); await settle();
                assert.match(await page.locator('[data-notification-help]').innerText(), /受系统策略限制/);
                assert.match(await page.locator('[data-notification-advice]').innerText(), /还不能确定/);
                assert.equal(await page.locator('#modal [data-action="notificationProbe"]').isDisabled(), true);
                assert.equal(await page.evaluate(() => (__mock.actions || []).includes('permission')), false);
                if (output) { fs.mkdirSync(output, { recursive:true });await page.screenshot({path:path.join(output,'policy-help.png'),fullPage:true}); }
            });
            await test('unknown policy reads do not falsely claim a block or suppress ordinary requests', async () => {
                await page.evaluate(async () => { __mock.state.permissions.notificationPolicy='unknown';await window.refreshNative(true); });
                assert.match(await page.locator('[data-notification-help]').innerText(), /暂时无法读取/);
                assert.doesNotMatch(await page.locator('[data-notification-advice]').innerText(), /系统报告通知权限受策略限制/);
                await page.locator('#modal [data-action="closeModal"]').click();
                await page.locator('[data-permission="notifications"]').click();await settle();
                assert.equal(await page.evaluate(() => __mock.lastPermission), 'notifications');
            });
            await test('policy removal and real grant update help and reenable the notification test', async () => {
                await page.evaluate(async () => { __mock.state.permissions.notificationPolicy='revoked';await window.refreshNative(true); });
                await page.locator('[data-permission="notifications"]').click();await settle();
                await page.evaluate(async () => {
                    Object.assign(__mock.state.permissions,{notificationPolicy:'not_revoked',notificationRuntime:true,notificationAppEnabled:true,notifications:true});
                    await window.refreshNative(true);
                });
                assert.match(await page.locator('[data-notification-help]').innerText(), /系统已允许发送通知/);
                assert.equal(await page.locator('#modal [data-action="notificationProbe"]').isDisabled(),false);
                await page.locator('#modal [data-action="notificationProbe"]').click();await settle();
                assert.equal(await page.evaluate(() => __mock.probes),1);
                await page.locator('#modal [data-action="closeModal"]').click();
                assert.equal(await page.locator('[data-permission="notifications"] .badge').innerText(),'已就绪');
            });
            await test('component export requires the explicit choice and diagnostics-only stays available', async () => {
                await reset('settings');
                await page.locator('[data-action="notificationReport"]').click();await settle();
                assert.equal(await page.evaluate(() => __mock.actions.includes('exportNotificationReport')),false);
                await page.locator('[data-action="reportOnly"]').click();await settle();
                assert.equal(await page.evaluate(() => __mock.lastReportOptions.includeComponents),false);
                await page.locator('[data-action="reportWithComponents"]').click();await settle();
                assert.equal(await page.evaluate(() => __mock.lastReportOptions.includeComponents),true);
                assert.equal(await page.evaluate(() => __mock.state.permissions.notifications),false);
                await page.locator('#modal [data-action="closeModal"]').click();
            });
            await test('running service with blocked notifications is not shown as a visible persistent card', async () => {
                await reset('settings');
                await page.evaluate(async () => {
                    __mock.state.enabled=true;__mock.state.running=true;
                    __mock.state.watchNotification.status='permission_blocked';
                    await window.refreshNative();
                });
                assert.match(await page.locator('[data-watch-notice]').innerText(),/通知被系统拦截/);
                assert.match(await page.locator('[data-watch-notice]').innerText(),/前台服务正在运行/);
                assert.equal(await page.locator('[data-permission="notifications"] .badge').innerText(),'去设置');
            });
            await test('watch status updates in place for grant, channel denial and interrupted service', async () => {
                for (const [status,copy] of [['registered','前台守候已运行'],['channel_blocked','守候通知通道已关闭'],['not_posted','系统暂未列出守候通知'],['interrupted','后台服务待恢复'],['stopped','尚未开启']]) {
                    await page.evaluate(async status => {__mock.state.watchNotification.status=status;await window.refreshNative();},status);
                    assert.match(await page.locator('[data-watch-notice]').innerText(),new RegExp(copy));
                }
            });
            await test('persistent notification channel uses its own settings entry', async () => {
                await page.locator('[data-watch-notice] [data-permission="watchChannel"]').click();await settle();
                assert.equal(await page.evaluate(() => __mock.lastPermission),'watchChannel');
                await page.locator('[data-watch-notice] [data-action="watchNotificationHelp"]').click();await settle();
                assert.equal(await page.locator('[data-notification-help]').count(),1);
                await page.locator('#modal [data-action="closeModal"]').click();
            });
            await test('a stopped watch is reported, while a sleeping phone only defers it', async () => {
                await reset();
                // Enabled but the service is gone: this is the one case that deserves the alarm.
                await page.evaluate(async () => {
                    __mock.state.enabled = true; __mock.state.running = false;
                    __mock.state.serviceHeartbeatAt = Date.now() - 30 * 60000;
                    await window.refreshNative(true);
                });
                assert.match(await page.locator('#content').innerText(), /守候服务已中断/);
                assert.match(await page.locator('#content').innerText(), /自启动/);
                // Swiping the app away rejects alarms too, so the guidance has to name the recents lock.
                assert.match(await page.locator('#content').innerText(), /最近任务/);
                await page.evaluate(async () => { __mock.state.permissions.accessibility = false; await window.refreshNative(true); });
                assert.match(await page.locator('#content').innerText(), /开启无障碍守候辅助/);
                await page.locator('[data-action="accessibilityInfo"]').click();
                assert.match(await page.locator('#modal').innerText(), /由系统自己重新绑定/);
                await page.locator('#modal [data-action="closeModal"]').last().click();

                // The service is alive but its heartbeat is old: doze delays the heartbeat and the
                // checks alike, so this must not be announced as an interruption. Warning at three
                // minutes made every night on a dozing phone look like a failure.
                await page.evaluate(async () => {
                    __mock.state.running = true; __mock.state.serviceHeartbeatAt = Date.now() - 5 * 60000;
                    await window.refreshNative(true);
                });
                assert.doesNotMatch(await page.locator('#content').innerText(), /守候服务已中断/);
                assert.doesNotMatch(await page.locator('#content').innerText(), /最近任务/);

                // Ten minutes of silence with the service still up is worth a quiet note, not a warning.
                await page.evaluate(async () => {
                    __mock.state.serviceHeartbeatAt = Date.now() - 30 * 60000;
                    await window.refreshNative(true);
                });
                const soft = await page.locator('#content').innerText();
                assert.doesNotMatch(soft, /守候服务已中断/);
                assert.match(soft, /仍在运行/);

                // A fresh heartbeat and a live service: nothing at all.
                await page.evaluate(async () => {
                    __mock.state.serviceHeartbeatAt = Date.now();
                    await window.refreshNative(true);
                });
                const healthy = await page.locator('#content').innerText();
                assert.doesNotMatch(healthy, /守候服务已中断/);
                assert.doesNotMatch(healthy, /仍在运行/);
                await page.evaluate(async () => { __mock.state.enabled = false; __mock.state.running = false; await window.refreshNative(true); });
            });
            await test('a recovered check clears the stale network banner', async () => {
                await reset();
                await page.evaluate(async () => { __mock.state.networkError = '网络未连接，联网后会自动重试'; await window.refreshNative(true); });
                assert.match(await page.locator('#content').innerText(), /网络未连接/);
                // Once a check succeeds the banner must retire, exactly as 1.0.5 did.
                await page.evaluate(async () => { __mock.state.networkError = ''; await window.refreshNative(true); });
                assert.doesNotMatch(await page.locator('#content').innerText(), /网络未连接/);
            });
            await test('a refused background start is reported and retires after a successful check', async () => {
                await reset();
                await page.evaluate(async () => { __mock.state.startError = '后台启动被系统限制，请打开应用重新开启守候'; await window.refreshNative(true); });
                assert.match(await page.locator('#content').innerText(), /后台启动被系统限制/);
                // The service did come up later, so the old start failure must not stay on the page.
                await page.evaluate(async () => { __mock.state.startError = ''; __mock.state.serviceError = ''; await window.refreshNative(true); });
                assert.doesNotMatch(await page.locator('#content').innerText(), /后台启动被系统限制/);
            });
            await test('the footer reports the version the bridge returns', async () => {
                await reset('settings');
                await page.evaluate(async () => { __mock.state.version = '9.9.9'; await window.refreshNative(true); });
                assert.match(await page.locator('#content').innerText(), /VR闹钟 9\.9\.9/);
                await page.evaluate(async () => { __mock.state.version = '1.1.0'; await window.refreshNative(true); });
                assert.match(await page.locator('#content').innerText(), /VR闹钟 1\.1\.0/);
            });
            await test('anchor page lists every broadcaster with its own watch switch', async () => {
                await reset('anchors');
                const copy = await page.locator('#content').innerText();
                assert.match(copy, /灰泽满 Hazel/);
                assert.match(copy, /另一位主播/);
                assert.match(copy, /本周 3 场/);
                assert.match(copy, /近 30 天 11 场/);
                assert.match(copy, /正在直播/);
                assert.equal(await page.locator('[data-toggle="anchor:hazel"]').getAttribute('aria-checked'), 'true');
                assert.equal(await page.locator('[data-toggle="anchor:other"]').getAttribute('aria-checked'), 'false');
                // The cached avatar is served by the app's own route and must actually render;
                // a regression here used to hide behind the letter placeholder.
                await page.waitForFunction(() => [...document.querySelectorAll('.anchor-art img')].some(img => img.complete && img.naturalWidth > 0));
                assert.equal(await page.locator('.anchor-art img').count(), 1);
                assert.equal(await page.locator('.anchor-art .anchor-letter').innerText(), '灰');
                assert.equal(await page.locator('.anchor-art.placeholder').last().innerText(), '另');
            });
            await test('the anchor page is a navigation tab and each anchor keeps its own switch', async () => {
                await reset();
                assert.equal(await page.locator('#nav button').count(), 6);
                await page.locator('[data-route="anchors"]').last().click();
                assert.match(await page.locator('#content').innerText(), /YOUR BROADCASTERS/);
                await page.locator('[data-toggle="anchor:other"]').click(); await settle();
                const saved = await page.evaluate(() => __mock.lastAnchor);
                assert.equal(saved.id, 'other');
                assert.equal(saved.enabled, true);
                assert.equal(saved.room, 333444);
                assert.equal(await page.locator('[data-toggle="anchor:hazel"]').getAttribute('aria-checked'), 'true');
            });
            await test('adding an anchor resolves the uid before saving', async () => {
                await reset('anchors');
                await page.locator('[data-action="addAnchor"]').click();
                await page.fill('#anchor-room', '555666');
                await page.locator('[data-action="resolveAnchor"]').click(); await settle();
                // Bare digits are a uid: that is the lookup the field now asks for.
                assert.equal(await page.evaluate(() => __mock.lastResolve.room), 0);
                assert.equal(await page.evaluate(() => __mock.lastResolve.uid), 555666);
                assert.equal(await page.inputValue('#anchor-name'), '识别到的主播');
                assert.match(await page.locator('#anchor-preview').innerText(), /UID 555666/);
                // The uid the user typed stays in the field instead of being swapped for a room id.
                assert.equal(await page.inputValue('#anchor-room'), '555666');
                await page.locator('[data-action="saveAnchorDraft"]').click(); await settle();
                const saved = await page.evaluate(() => __mock.lastAnchor);
                assert.equal(saved.name, '识别到的主播');
                assert.equal(saved.uid, 555666);
                assert.equal(saved.room, 1713546334);
                assert.equal(await page.locator('#modal').isVisible(), false);
            });
            await test('a changed room number cannot reuse the previously resolved identity', async () => {
                await reset('anchors');
                await page.locator('[data-action="addAnchor"]').click();
                await page.fill('#anchor-room', 'https://live.bilibili.com/555666');
                await page.locator('[data-action="resolveAnchor"]').click(); await settle();
                await page.fill('#anchor-room', 'https://live.bilibili.com/777888');
                await page.locator('[data-action="saveAnchorDraft"]').click(); await settle();
                assert.match(await page.locator('#anchor-error').innerText(), /识别主播信息/);
                assert.equal(await page.evaluate(() => __mock.lastAnchor), undefined);
                assert.equal(await page.locator('#modal').isVisible(), true);
            });
            await test('a failed room lookup reports the reason and keeps the dialog open', async () => {
                await reset('anchors');
                await page.locator('[data-action="addAnchor"]').click();
                await page.fill('#anchor-room', 'https://live.bilibili.com/40404');
                await page.locator('[data-action="resolveAnchor"]').click(); await settle();
                assert.match(await page.locator('#anchor-error').innerText(), /没有找到这个直播间/);
                assert.equal(await page.locator('#modal').isVisible(), true);
            });
            await test('a uid whose account has no live room is reported without blaming the input', async () => {
                await reset('anchors');
                await page.evaluate(() => { __mock.resolveError = '这个 UID 还没有开通直播间，请改用直播间链接'; });
                await page.locator('[data-action="addAnchor"]').click();
                await page.fill('#anchor-room', '555666');
                await page.locator('[data-action="resolveAnchor"]').click(); await settle();
                assert.match(await page.locator('#anchor-error').innerText(), /还没有开通直播间/);
                assert.equal(await page.locator('#modal').isVisible(), true);
                await page.evaluate(() => { __mock.resolveError = null; });
            });
            await test('the ring switch silences one anchor without touching detection', async () => {
                await reset('anchors');
                await page.locator('[data-toggle="ring:hazel"]').click(); await settle();
                const saved = await page.evaluate(() => __mock.lastAnchor);
                assert.equal(saved.id, 'hazel');
                assert.equal(saved.alarm, false);
                assert.equal(saved.enabled, true);
                assert.equal(await page.locator('[data-toggle="anchor:hazel"]').getAttribute('aria-checked'), 'true');
                assert.equal(await page.locator('[data-toggle="ring:hazel"]').getAttribute('aria-checked'), 'false');
                await page.locator('[data-toggle="ring:hazel"]').click(); await settle();
                assert.equal(await page.evaluate(() => __mock.lastAnchor.alarm), true);
            });
            await test('the week page is a tab that groups entries by day with live state', async () => {
                await reset();
                await page.locator('[data-route="week"]').last().click();
                const copy = await page.locator('#content').innerText();
                assert.match(copy, /WEEKLY TIMELINE/);
                assert.match(copy, /游戏/);
                assert.match(copy, /20:00 – 21:00/);
                assert.match(copy, /正在直播/);
                assert.doesNotMatch(copy, /周表图片/);
            });
            await test('week rows keep the state label, name and time on a single line', async () => {
                await reset();
                await page.locator('[data-route="week"]').last().click();
                const m = await page.evaluate(() => {
                    const read = sel => {
                        const el = document.querySelector(sel);
                        if (!el) return null;
                        const box = el.getBoundingClientRect();
                        const cs = getComputedStyle(el);
                        return { h: Math.round(box.height), ws: cs.whiteSpace, text: el.innerText };
                    };
                    return { state: read('.sched-state'), time: read('.sched-time'), name: read('.sched-head .anchor-name') };
                });
                // Four squeezed items used to share one line, wrapping the state label one
                // character per row; a single line is the regression guard for that.
                assert.ok(m.state, 'the row exposes a state label');
                assert.equal(m.state.ws, 'nowrap');
                assert.ok(m.state.h <= 20, `state label is one line, got ${m.state.h}px`);
                assert.ok(m.time && m.time.ws === 'nowrap' && m.time.h <= 26, `time is one line, got ${m.time && m.time.h}px`);
                assert.ok(m.name && m.name.h <= 26, `name is one line, got ${m.name && m.name.h}px`);
                // The layout must not fall back to the shared .row / .live-info styles.
                assert.equal(await page.locator('.sched-card .live-info').count(), 0);
                assert.equal(await page.locator('.sched-card .sched-head').count(), 1);
            });
            await test('the schedule editor parses pasted text into confirmed entries', async () => {
                await reset('anchors');
                await page.locator('[data-anchor-schedule="hazel"]').click();
                assert.match(await page.locator('#modal').innerText(), /已有安排/);
                await page.fill('#sched-text', '周五 20:00 游戏回'+String.fromCharCode(10)+'周六 21:30 杂谈');
                await page.locator('[data-action="parseScheduleText"]').click(); await settle();
                assert.match(await page.locator('#schedule-editor').innerText(), /联动回|游戏回/);
                await page.locator('[data-action="saveSchedule"]').click(); await settle();
                const saved = await page.evaluate(() => __mock.lastSchedule);
                assert.equal(saved.id, 'hazel');
                assert.equal(saved.entries.length, 3);
                assert.equal(await page.locator('#modal').isVisible(), false);
                const texts = await page.evaluate(() => __mock.lastScheduleText);
                assert.match(texts.text, /游戏回/);
            });
            await test('an added schedule entry flows through the weekday picker', async () => {
                await reset('anchors');
                await page.locator('[data-anchor-schedule="other"]').click();
                await page.locator('#schedule-editor [data-sched-weekday="0"]').click();
                await page.locator('#schedule-editor [data-sched-weekday="2"]').click();
                await page.fill('#sched-start', '19:00');
                await page.fill('#sched-note', '新安排');
                await page.locator('[data-action="addScheduleEntry"]').click();
                await page.locator('[data-action="saveSchedule"]').click(); await settle();
                const saved = await page.evaluate(() => __mock.lastSchedule);
                assert.equal(saved.id, 'other');
                assert.equal(saved.entries.length, 1);
                assert.equal(saved.entries[0].days, 5);
                assert.equal(saved.entries[0].start, 1140);
                assert.equal(saved.entries[0].note, '新安排');
            });
            await test('an uploaded schedule picture is shown on the week page and in a viewer', async () => {
                await reset('anchors');
                await page.evaluate(async () => { __mock.state.anchors[0].scheduleImage = true; await window.refreshNative(true); });
                await page.locator('[data-route="week"]').last().click();
                assert.match(await page.locator('#content').innerText(), /周表图片/);
                await page.locator('[data-action="viewScheduleImage"]').click();
                const src = await page.locator('#modal .sched-img img').getAttribute('src');
                assert.equal(src, '/schedule/hazel.img');
                await page.waitForFunction(() => { const i = document.querySelector('#modal .sched-img img'); return i && i.complete && i.naturalWidth > 0; });
                await page.locator('#modal [data-action="closeModal"]').last().click();
                await page.locator('[data-route="anchors"]').last().click();
                await page.locator('[data-anchor-schedule="hazel"]').click();
                assert.match(await page.locator('#schedule-editor').innerText(), /删除图片/);
                await page.locator('[data-action="removeScheduleImage"]').click(); await settle();
                assert.equal(await page.evaluate(() => __mock.lastRemoveImage), 'hazel');
            });
            await test('accent colour and card opacity win over the dark theme defaults', async () => {
                await reset('settings');
                await page.selectOption('[data-setting="theme"]', 'dark');
                await page.waitForFunction(() => document.body.classList.contains('dark'));
                await page.locator('[data-seed="#A65C83"]').click(); await settle();
                await page.evaluate(async () => { __mock.state.config.cardOpacity = 80; await window.refreshNative(true); });
                const computed = await page.evaluate(() => {
                    const cs = getComputedStyle(document.querySelector('.card'));
                    return { primary: cs.getPropertyValue('--primary').replace(/\s+/g, ''), card: cs.getPropertyValue('--card').replace(/\s+/g, '') };
                });
                // rgb(171,194,214) is the stylesheet's dark default; the picked seed must beat it.
                assert.notEqual(computed.primary, 'rgb(171,194,214)');
                assert.match(computed.card, /rgba\(36,44,53,0\.8\)/);
                await page.selectOption('[data-setting="theme"]', 'light'); await settle();
            });
            await test('the theme card holds the accent and AMOLED controls without a second conflicting section', async () => {
                await reset('settings');
                const copy = await page.locator('#content').innerText();
                // One heading owns明暗/主题色/AMOLED; the old split into two sections read as a conflict.
                assert.match(copy, /主题与外观/);
                assert.equal((copy.match(/界面主题/g) || []).length, 1);
                assert.match(copy, /主题色/);
                assert.match(copy, /AMOLED/);
                assert.doesNotMatch(copy, /外观与个性化/);
                assert.equal(await page.locator('[data-setting="theme"]').count(), 1);
                assert.equal(await page.locator('[data-toggle="amoled"]').count(), 1);
            });
            await test('a swatch previews the colour that is actually applied in the current theme', async () => {
                await reset('settings');
                const lightSwatch = await page.evaluate(() => {
                    const b = [...document.querySelectorAll('.swatch')].find(x => x.dataset.seed === '#A65C83');
                    return getComputedStyle(b).backgroundColor;
                });
                await page.selectOption('[data-setting="theme"]', 'dark');
                await page.waitForFunction(() => document.body.classList.contains('dark'));
                const darkSwatch = await page.evaluate(() => {
                    const b = [...document.querySelectorAll('.swatch')].find(x => x.dataset.seed === '#A65C83');
                    return getComputedStyle(b).backgroundColor;
                });
                // Dark mode lightens the accent, so the dot must change with the theme.
                assert.notEqual(lightSwatch, darkSwatch);
                await page.selectOption('[data-setting="theme"]', 'light'); await settle();
            });
            await test('pasting a live or space link resolves the anchor', async () => {
                await reset('anchors');
                await page.locator('[data-action="addAnchor"]').click();
                await page.fill('#anchor-room', 'https://live.bilibili.com/1713546334?from=search');
                await page.locator('[data-action="resolveAnchor"]').click(); await settle();
                let sent = await page.evaluate(() => __mock.lastResolve);
                assert.equal(sent.room, 1713546334);
                assert.equal(sent.uid, 0);
                await page.fill('#anchor-room', 'https://space.bilibili.com/1298779265');
                await page.locator('[data-action="resolveAnchor"]').click(); await settle();
                sent = await page.evaluate(() => __mock.lastResolve);
                assert.equal(sent.room, 0);
                assert.equal(sent.uid, 1298779265);
                await page.fill('#anchor-room', 'UID 1234567');
                await page.locator('[data-action="resolveAnchor"]').click(); await settle();
                sent = await page.evaluate(() => __mock.lastResolve);
                assert.equal(sent.uid, 1234567);
            });
            await test('the background picture renders and can be removed', async () => {
                await reset('settings');
                await page.evaluate(async () => { __mock.state.backgroundSet = true; __mock.state.backgroundRevision = 'aa11'; __mock.state.backgroundName = '夜色.png'; await window.refreshNative(true); });
                assert.equal(await page.evaluate(() => document.body.classList.contains('has-bg')), true);
                const url = await page.evaluate(() => document.getElementById('bg-layer').style.backgroundImage);
                assert.match(url, /\/background\/current/);
                // The address must carry a revision, or every later picture would be served
                // from the browser cache under the same URL.
                assert.match(url, /\?v=aa11/);
                await page.waitForFunction(() => { const i = document.getElementById('bg-layer'); const u = i && i.style.backgroundImage; return !!u; });
                await page.locator('[data-action="removeBackground"]').click(); await settle();
                assert.equal(await page.evaluate(() => __mock.removedBackground), true);
                assert.equal(await page.evaluate(() => document.body.classList.contains('has-bg')), false);
            });
            await test('replacing the background changes the image address so the new picture loads', async () => {
                await reset('settings');
                await page.evaluate(async () => { __mock.state.backgroundSet = true; __mock.state.backgroundRevision = 'aa11'; await window.refreshNative(true); });
                const first = await page.evaluate(() => document.getElementById('bg-layer').style.backgroundImage);
                await page.locator('[data-action="pickBackground"]').first().click(); await settle();
                await page.evaluate(async () => { __mock.state.backgroundRevision = 'bb22'; await window.refreshNative(true); });
                const second = await page.evaluate(() => document.getElementById('bg-layer').style.backgroundImage);
                assert.notEqual(first, second);
                assert.match(second, /\?v=bb22/);
                assert.equal(await page.evaluate(() => __mock.pickedBackground), true);
            });
            await test('picking a schedule image repaints the editor immediately', async () => {
                await reset('anchors');
                await page.evaluate(async () => { __mock.state.anchors[0].scheduleImage = true; await window.refreshNative(true); });
                await page.locator('[data-anchor-schedule="hazel"]').click();
                await page.locator('[data-action="pickScheduleImage"]').click(); await settle();
                assert.equal(await page.evaluate(() => __mock.lastPickImage), 'hazel');
                assert.equal(await page.evaluate(() => scheduleDraft.hasImage), true);
                const src = await page.locator('#modal .sched-img img').getAttribute('src');
                assert.equal(src, '/schedule/hazel.img');
                assert.match(await page.locator('#schedule-editor').innerText(), /周表图片对照/);
            });
            await test('the bulk avatar refresh asks once and reports the count', async () => {
                await reset('anchors');
                await page.locator('[data-action="refreshAvatars"]').click(); await settle();
                assert.equal(await page.evaluate(() => __mock.refreshedAvatars), true);
                assert.match(await page.locator('#toast').innerText(), /已为 2 位主播刷新头像/);
            });
            await test('AI recognition turns the uploaded picture into confirmed entries', async () => {
                await reset('anchors');
                await page.evaluate(async () => { __mock.state.anchors[0].scheduleImage = true; await window.refreshNative(true); });
                await page.locator('[data-anchor-schedule="hazel"]').click();
                await page.locator('[data-action="ocrSchedule"]').click(); await settle();
                const ocr = await page.evaluate(() => __mock.lastOcr);
                assert.equal(ocr.id, 'hazel');
                const editor = await page.locator('#schedule-editor').innerText();
                assert.match(editor, /已有安排\s*4 \/ 16/);
                const mondayFirst = (new Date().getDay()+6)%7;
                assert.equal(await page.evaluate(() => scheduleDraft.entries[3].days), 1 << mondayFirst, "the date-only row lands on today's weekday");
                assert.match(await page.evaluate(() => scheduleDraft.entries[3].note), /生日会/);
                assert.match(editor, /AI游戏/);
                assert.match(editor, /21:00/);
                // Nothing is saved until the user confirms.
                assert.equal(await page.evaluate(() => __mock.lastSchedule), undefined);
            });
            await test('deleting an anchor asks first and then removes only that anchor', async () => {
                await reset('anchors');
                await page.locator('[data-anchor-edit="other"]').click();
                await page.locator('[data-action="deleteAnchor"]').click();
                assert.match(await page.locator('#modal').innerText(), /删除这位主播/);
                assert.equal(await page.evaluate(() => __mock.lastDeleted), undefined);
                await page.locator('[data-action="confirmDeleteAnchor"]').click(); await settle();
                assert.equal(await page.evaluate(() => __mock.lastDeleted), 'other');
                const copy = await page.locator('#content').innerText();
                assert.doesNotMatch(copy, /另一位主播/);
                assert.match(copy, /灰泽满 Hazel/);
            });
            await test('theme section paints seed swatches and applies the picked accent', async () => {
                await reset('settings');
                const copy = await page.locator('#content').innerText();
                assert.match(copy, /主题与外观/);
                assert.match(copy, /背景图片/);
                assert.equal(await page.locator('.swatch').count(), 13);
                await page.locator('[data-seed="#A65C83"]').click(); await settle();
                assert.equal(await page.evaluate(() => __mock.state.config.seedColor), '#A65C83');
                assert.equal((await page.evaluate(() => getComputedStyle(document.documentElement).getPropertyValue('--primary'))).replace(/\s+/g, ''), 'rgb(166,92,131)');
                await page.locator('[data-seed=""]').click(); await settle();
                assert.equal(await page.evaluate(() => __mock.state.config.seedColor), '');
            });
            await test('AMOLED applies in dark theme and hide-recents toggle persists', async () => {
                await reset('settings');
                await page.locator('[data-toggle="amoled"]').click(); await settle();
                assert.equal(await page.evaluate(() => __mock.state.config.amoled), true);
                assert.equal(await page.evaluate(() => document.body.classList.contains('amoled')), false);
                await page.selectOption('[data-setting="theme"]', 'dark');
                await page.waitForFunction(() => document.body.classList.contains('dark'));
                assert.equal(await page.evaluate(() => document.body.classList.contains('amoled')), true);
                await page.locator('[data-toggle="hideRecents"]').click(); await settle();
                assert.equal(await page.evaluate(() => __mock.state.config.hideRecents), true);
            });
            await test('continuous mode is on by default and can be switched off', async () => {
                await reset('settings');
                const copy = await page.locator('#content').innerText();
                assert.match(copy, /持续高频守候/);
                assert.equal(await page.evaluate(() => __mock.state.config.turbo), true);
                await page.locator('[data-toggle="turbo"]').click(); await settle();
                assert.equal(await page.evaluate(() => __mock.state.config.turbo), false);
            });
            if (output) {
                fs.mkdirSync(output, { recursive: true });
                await page.goto(`http://127.0.0.1:${server.address().port}/`);
                await page.waitForSelector('[data-route="anchors"]'); await settle();
                await page.locator('[data-route="anchors"]').last().click();
                await page.screenshot({ path: path.join(output, 'anchors-list.png'), fullPage: true });
                await page.locator('[data-action="addAnchor"]').click();
                await page.fill('#anchor-room', '555666');
                await page.locator('[data-action="resolveAnchor"]').click(); await settle();
                await page.screenshot({ path: path.join(output, 'anchors-add.png'), fullPage: true });
                await page.locator('#modal [data-action="closeModal"]').last().click();
                await page.screenshot({ path: path.join(output, 'permissions-fixed.png'), fullPage: true });
                await page.locator('[data-route="sound"]').click();
                await page.locator('[data-tone="morning"]').click(); await settle();
                await page.screenshot({ path: path.join(output, 'sound-fixed.png'), fullPage: true });
            }
            assert.deepEqual(errors, [], 'no browser script errors');
        }
        console.log(`PASS: ${results.length} ${legacy ? 'original regression reproductions' : 'UI and bridge regression scenarios'}`);
        if (output) fs.writeFileSync(path.join(output, legacy ? 'original-regressions.json' : 'ui-regressions.json'), JSON.stringify({ legacy, results, errors }, null, 2));
    } finally {
        await browser.close(); server.close();
    }
})().catch(error => { console.error(error); server.close(); process.exitCode = 1; });
