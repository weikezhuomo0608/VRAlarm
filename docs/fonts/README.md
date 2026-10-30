# 打包进应用的字体

包里目前只有一个字体文件，只为一个字：

- `app/src/main/assets/kao-mark.ttf` —— **「考」**（U+8003），给「考子」卡片左上角那枚记号用。
- 来源：开源毛笔字体 **Ma Shan Zheng（马善政毛笔楷书）**，The MaShanZheng Project Authors，**SIL Open Font License 1.1**（全文见同目录 `OFL-MaShanZheng.txt`）。
- 它是**单字子集**（3 个字形、约 2 KB），不是整个字体，所以体积可以忽略；OFL 允许子集与再分发，本项目保留其版权与许可证文本。

## 为什么要随包带字体

Android 系统**不带隶书、行楷一类的书法字体**（只有 Noto Sans/Serif CJK 这类黑体与宋体），而商业书法字体（方正隶书、华文行楷等）不允许塞进要公开发布的应用。所以想要一个真的毛笔字，唯一干净的路子是：**取一个开源字体的单字子集**，随包带上。

## 怎么重新取字（换字、换字体、换字号都走这套）

按字取字接口只会返回包含这些字的极小子集，不需要下载整个字体：

```bash
# 1) 问 Google Fonts 要「考」这一个字的字体（把 text= 换成需要的字，URL 编码后即可，多个字用逗号分隔）
curl -s "https://fonts.googleapis.com/css2?family=Ma+Shan+Zheng&text=%E8%80%83" -o /tmp/kao.css
# 2) 从返回的 CSS 里取出字体地址并下载（子集约 2 KB）
#    src: url(https://fonts.gstatic.com/l/font?kit=...&v=18) format('truetype')
curl -s -o app/src/main/assets/kao-mark.ttf "https://fonts.gstatic.com/l/font?kit=…"
```

然后：

- 在 `app/src/main/assets/style.css` 顶部的 `@font-face` 里保持 `font-family` 与文件名一致；「考」字那条规则的 `font-family` 把该家族名放在**第一位**（后面照旧跟隶书/宋体等系统名兜底）。
- 想确认子集里确实有那个字、并看字体名与版权行：`python -c "from fontTools.ttLib import TTFont;f=TTFont('app/src/main/assets/kao-mark.ttf');print(f.getBestCmap(),f['name'].getDebugName(0))"`（本机已装 fontTools）。
- 换字体时**把新的 OFL 文本一起换掉**（同目录），并更新 `README.md` 的「素材来源」——字体也是要署名的素材，和宠物形象一个道理。
- 其他可选的 OFL 毛笔/行书字体：`Zhi Mang Xing`（志莽行书，更草）、`Long Cang`（龙藏）、`Liu Jian Mao Cao`（柳建毛草）。换 `family=` 即可，取字方式完全一样。
