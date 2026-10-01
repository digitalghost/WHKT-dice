#!/usr/bin/env ruby

require "open3"

input, output = ARGV
abort "Usage: extract_pdf_rules.rb INPUT.pdf OUTPUT.md" unless input && output

text, error, status = Open3.capture3("pdftotext", input, "-")
abort "pdftotext failed: #{error}" unless status.success?

pages = text.split("\f", -1)
pages.pop while pages.last&.strip == ""
abort "Expected 113 pages, extracted #{pages.length}" unless pages.length == 113

header = <<~MARKDOWN
  # 《杀戮小队 三版 官中修订版 v11.5》全文提取

  - 来源文件：`杀戮小队 三版 官中修订版 v11.5.pdf`
  - PDF 页数：113
  - 提取方式：按 PDF 文字层逐页提取，并保留原始页码锚点
  - 用途：项目内规则检索、需求回溯与实现核对

  > 本文件是来源 PDF 的可搜索文字层副本，不替代原始排版。原文中的字体颜色、图片、卡牌图和部分复杂表格无法完整表达为纯文本；遇到表格、示意图或文字顺序歧义时，请回看同页 PDF。页内文字未做规则改写，以避免把实现假设混入官方规则。

  ## 快速索引

  - [修订记录与目录（PDF 1-6 页）](#pdf-001)
  - [核心规则（PDF 7 页起）](#pdf-007)
  - [战略阶段（PDF 8 页）](#pdf-008)
  - [交战阶段（PDF 9 页）](#pdf-009)
  - [射击行动（PDF 15 页）](#pdf-015)
  - [近战行动（PDF 17 页）](#pdf-017)
  - [重要概念（PDF 19 页）](#pdf-019)
  - [地形与移动（PDF 31 页）](#pdf-031)
  - [认证行动任务包 2025（PDF 105 页）](#pdf-105)
  - [部署与四个转折点（PDF 106 页）](#pdf-106)
  - [结束战斗与击杀行动（PDF 107 页）](#pdf-107)
  - [关键行动（PDF 108 页）](#pdf-108)
  - [战术行动（PDF 109 页）](#pdf-109)
  - [射击与地形完整流程（PDF 112 页）](#pdf-112)

MARKDOWN

body = pages.each_with_index.map do |page, index|
  number = index + 1
  cleaned = page.lines.map(&:rstrip).join("\n").strip
  <<~MARKDOWN
    <a id="pdf-#{format('%03d', number)}"></a>

    ## PDF 第 #{number} 页

    ```text
    #{cleaned}
    ```
  MARKDOWN
end.join("\n")

File.write(output, header + body, encoding: "UTF-8")
puts "Extracted #{pages.length} pages to #{output}"
