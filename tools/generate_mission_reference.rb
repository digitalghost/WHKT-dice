#!/usr/bin/env ruby
# frozen_string_literal: true

# Generates the in-app mission quick-reference poster from MissionCards.kt so
# the raster asset cannot silently drift away from the rules shown in the UI.

require "cgi"

ROOT = File.expand_path("..", __dir__)
SOURCE = File.join(ROOT, "app/src/main/java/com/example/helloworld/MissionCards.kt")
OUTPUT = ARGV.fetch(0, File.join(ROOT, "tmp/mission-reference-v2.svg"))

WIDTH = 2800
HEIGHT = 2000
MARGIN = 48
FONT = "PingFang SC, Hiragino Sans GB, sans-serif"

BG = "#0F0F0F"
SURFACE = "#191919"
SURFACE_2 = "#20201F"
LINE = "#343432"
INK = "#EBEBE9"
MUTED = "#A6A6A4"
ORANGE = "#EE7540"

def esc(value)
  CGI.escapeHTML(value.to_s)
end

def unescape_kotlin(value)
  value.gsub('\\n', "\n").gsub('\\"', '"').gsub('\\\\', '\\')
end

source = File.read(SOURCE)
cards = source.scan(/MissionCard\("((?:\\.|[^"])*)",\s*"((?:\\.|[^"])*)",\s*"((?:\\.|[^"])*)",\s*"((?:\\.|[^"])*)",\s*"((?:\\.|[^"])*)"\)/m).map do |row|
  name, category, reveal, rule, scoring = row.map { |value| unescape_kotlin(value) }
  { name: name, category: category, reveal: reveal, rule: rule, scoring: scoring }
end
abort "Expected 21 mission cards, found #{cards.length}" unless cards.length == 21

common_match = source.match(/const val COMMON\s*=\s*"((?:\\.|[^"])*)"/m)
abort "COMMON text not found" unless common_match
common = unescape_kotlin(common_match[1]).sub("单个任务", "单个行动")

def char_units(char)
  char.ascii_only? ? 0.56 : 1.0
end

def wrap_text(text, max_units)
  paragraphs = text.to_s.split("\n", -1)
  paragraphs.flat_map do |paragraph|
    next [""] if paragraph.empty?

    lines = []
    line = +""
    units = 0.0
    paragraph.scan(/\X/).each do |char|
      width = char_units(char)
      if units + width > max_units && !line.empty?
        lines << line
        line = +""
        units = 0.0
      end
      line << char
      units += width
    end
    lines << line unless line.empty?
    lines
  end
end

def text_line(x, y, value, size:, color: INK, weight: 400, anchor: "start", opacity: 1.0, letter_spacing: 0)
  %(<text x="#{x}" y="#{y}" font-family="#{FONT}" font-size="#{size}" font-weight="#{weight}" fill="#{color}" text-anchor="#{anchor}" opacity="#{opacity}" letter-spacing="#{letter_spacing}">#{esc(value)}</text>)
end

def text_block(x, y, value, width:, size:, color: INK, weight: 400, line_height: 1.38, max_lines: nil)
  units = width.to_f / size
  lines = wrap_text(value, units)
  lines = lines.first(max_lines) if max_lines
  line_px = size * line_height
  body = lines.each_with_index.map do |line, index|
    %(<tspan x="#{x}" dy="#{index.zero? ? 0 : line_px}">#{esc(line)}</tspan>)
  end.join
  [%(<text x="#{x}" y="#{y}" font-family="#{FONT}" font-size="#{size}" font-weight="#{weight}" fill="#{color}">#{body}</text>), lines.length * line_px]
end

def card_accent(category)
  return "#4DA3FF" if category.include?("侦查")
  return "#49B9A7" if category.include?("安全防护")
  return ORANGE if category.include?("搜索与摧毁")
  return "#B98DEB" if category.include?("渗透")

  "#D7B36A"
end

def category_label(category)
  category
    .sub("关键目标", "关键行动")
    .sub("战术目标", "战术行动")
end

def card_svg(card, x, y, width, height)
  accent = card_accent(card[:category])
  body_width = width - 30
  font_size = 18.0

  loop do
    unit_width = body_width / font_size
    line_count = wrap_text(card[:rule], unit_width).length + wrap_text(card[:scoring], unit_width).length
    line_count += wrap_text(card[:reveal], unit_width).length unless card[:reveal].empty?
    sections = card[:reveal].empty? ? 2 : 3
    required = 92 + line_count * font_size * 1.34 + sections * 24 + sections * 7
    break if required <= height - 18 || font_size <= 14
    font_size -= 0.5
  end

  elements = []
  elements << %(<rect x="#{x}" y="#{y}" width="#{width}" height="#{height}" rx="12" fill="#{SURFACE}" stroke="#{LINE}" stroke-width="2"/>)
  elements << %(<rect x="#{x}" y="#{y}" width="#{width}" height="7" rx="4" fill="#{accent}"/>)
  elements << text_line(x + 15, y + 42, card[:name], size: 28, color: INK, weight: 700)
  elements << text_line(x + 15, y + 69, category_label(card[:category]), size: 15, color: accent, weight: 600)

  cursor = y + 100
  sections = []
  sections << ["公开时机", card[:reveal]] unless card[:reveal].empty?
  sections << ["行动规则", card[:rule]]
  sections << ["VP 结算", card[:scoring]]
  sections.each do |label, value|
    elements << text_line(x + 15, cursor, label, size: 14, color: label == "VP 结算" ? accent : MUTED, weight: 700, letter_spacing: 0.5)
    cursor += 22
    block, used = text_block(x + 15, cursor, value, width: body_width, size: font_size, color: INK, line_height: 1.34)
    elements << block
    cursor += used + 9
  end
  elements.join("\n")
end

svg = []
svg << %(<?xml version="1.0" encoding="UTF-8"?>)
svg << %(<svg xmlns="http://www.w3.org/2000/svg" width="#{WIDTH}" height="#{HEIGHT}" viewBox="0 0 #{WIDTH} #{HEIGHT}">)
svg << %(<rect width="#{WIDTH}" height="#{HEIGHT}" fill="#{BG}"/>)
svg << %(<rect x="0" y="0" width="12" height="#{HEIGHT}" fill="#{ORANGE}"/>)

# Header
svg << text_line(MARGIN, 76, "行动流程速查", size: 52, color: INK, weight: 700, letter_spacing: 1)
svg << text_line(MARGIN, 111, "关键行动 2025 · 战术行动 2026", size: 21, color: MUTED, weight: 500)
svg << text_line(WIDTH - MARGIN, 70, "KILL TEAM / TABLE REFERENCE", size: 20, color: ORANGE, weight: 700, anchor: "end", letter_spacing: 2)
svg << text_line(WIDTH - MARGIN, 104, "规则卡优先 · 歧义以最新勘误为准", size: 17, color: MUTED, anchor: "end")

# Five-step flow
flow_y = 136
flow_h = 112
flow_gap = 16
flow_w = (WIDTH - MARGIN * 2 - flow_gap * 4) / 5.0
flow = [
  ["01", "选择行动", "关键行动选 1；战术行动按小队原型选择"],
  ["02", "部署与暗选", "任务保持私密，按各卡片的公开时机揭示"],
  ["03", "执行任务行动", "通常从第 2 转折点开始；具体卡片优先"],
  ["04", "转折点结算", "检查控制、争夺、标记与每转折点上限"],
  ["05", "战斗结束结算", "处理携带标记、最终控制与额外得分"]
]
flow.each_with_index do |(number, title, detail), index|
  x = MARGIN + index * (flow_w + flow_gap)
  svg << %(<rect x="#{x}" y="#{flow_y}" width="#{flow_w}" height="#{flow_h}" rx="10" fill="#{SURFACE_2}" stroke="#{LINE}" stroke-width="2"/>)
  svg << text_line(x + 18, flow_y + 34, number, size: 17, color: ORANGE, weight: 700)
  svg << text_line(x + 62, flow_y + 35, title, size: 22, color: INK, weight: 700)
  detail_svg, = text_block(x + 18, flow_y + 69, detail, width: flow_w - 36, size: 15, color: MUTED, line_height: 1.3, max_lines: 2)
  svg << detail_svg
end

# Common rules and kill-score reference
common_y = 266
common_h = 184
left_w = 1646
right_x = MARGIN + left_w + 18
right_w = WIDTH - MARGIN - right_x
svg << %(<rect x="#{MARGIN}" y="#{common_y}" width="#{left_w}" height="#{common_h}" rx="10" fill="#{SURFACE}" stroke="#{LINE}" stroke-width="2"/>)
svg << text_line(MARGIN + 18, common_y + 34, "通用说明", size: 21, color: ORANGE, weight: 700)
common_svg, = text_block(MARGIN + 18, common_y + 68, common, width: left_w - 36, size: 18, color: INK, line_height: 1.45, max_lines: 4)
svg << common_svg
svg << %(<rect x="#{right_x}" y="#{common_y}" width="#{right_w}" height="#{common_h}" rx="10" fill="#{SURFACE}" stroke="#{LINE}" stroke-width="2"/>)
svg << text_line(right_x + 16, common_y + 30, "杀戮行动计分 · 队伍人数 5—14", size: 18, color: ORANGE, weight: 700)

kill_rows = [
  ["1VP", 1, 1, 1, 2, 2, 2, 2, 2, 3, 3],
  ["2VP", 2, 2, 3, 3, 4, 4, 4, 5, 5, 6],
  ["3VP", 3, 4, 4, 5, 5, 6, 7, 7, 8, 8],
  ["4VP", 4, 5, 6, 6, 7, 8, 9, 10, 10, 11],
  ["5VP", 5, 6, 7, 8, 9, 10, 11, 12, 13, 14]
]
headers = ["得分", *Array(5..14)]
table_x = right_x + 16
table_y = common_y + 44
cell_w = (right_w - 32) / headers.length.to_f
cell_h = 20
([headers] + kill_rows).each_with_index do |row, row_index|
  row.each_with_index do |value, column_index|
    x = table_x + column_index * cell_w
    y = table_y + row_index * cell_h
    fill = row_index.zero? ? "#292927" : (column_index.zero? ? "#26211F" : "#1D1D1C")
    svg << %(<rect x="#{x}" y="#{y}" width="#{cell_w}" height="#{cell_h}" fill="#{fill}" stroke="#{LINE}" stroke-width="1"/>)
    svg << text_line(x + cell_w / 2, y + 15, value, size: 12, color: column_index.zero? && row_index.positive? ? ORANGE : INK, weight: column_index.zero? ? 700 : 500, anchor: "middle")
  end
end
svg << text_line(right_x + 16, common_y + common_h - 12, "达到对应击杀数获得该分；结算时杀戮得分最高者额外 +1VP。", size: 13, color: MUTED)

# Section geometry
section_y = 474
cards_y = 532
bottom = 1940
gap = 14
left_section_w = 1664
right_section_x = MARGIN + left_section_w + 24
right_section_w = WIDTH - MARGIN - right_section_x
card_h = (bottom - cards_y - gap * 2) / 3.0

svg << text_line(MARGIN, section_y + 33, "战术行动 2026", size: 30, color: INK, weight: 700)
svg << text_line(MARGIN + 250, section_y + 32, "按小队原型选择 · 4 类 × 3 项", size: 17, color: MUTED)
svg << text_line(right_section_x, section_y + 33, "关键行动 2025", size: 30, color: INK, weight: 700)
svg << text_line(WIDTH - MARGIN, section_y + 32, "9 项", size: 17, color: MUTED, anchor: "end")

tactical_order = [
  %w[侧翼 回收 敌情],
  %w[插旗 殉道者 特使],
  %w[主宰 扫荡清理 击溃],
  %w[放置装置 追踪敌军 窃取情报]
]
key_order = [
  %w[占领 掠夺 传输情报],
  %w[宝球 宣告主权 能量电池],
  %w[下载 数据 重启]
]
by_name = cards.to_h { |card| [card[:name], card] }

tactical_w = (left_section_w - gap * 3) / 4.0
tactical_order.each_with_index do |column, column_index|
  column.each_with_index do |name, row_index|
    x = MARGIN + column_index * (tactical_w + gap)
    y = cards_y + row_index * (card_h + gap)
    svg << card_svg(by_name.fetch(name), x, y, tactical_w, card_h)
  end
end

key_w = (right_section_w - gap * 2) / 3.0
key_order.each_with_index do |row, row_index|
  row.each_with_index do |name, column_index|
    x = right_section_x + column_index * (key_w + gap)
    y = cards_y + row_index * (card_h + gap)
    svg << card_svg(by_name.fetch(name), x, y, key_w, card_h)
  end
end

svg << text_line(MARGIN, HEIGHT - 18, "依据应用内结构化任务数据生成 · 内容源：风来西行动速查 vol.1.29 · 非官方原卡", size: 14, color: MUTED)
svg << text_line(WIDTH - MARGIN, HEIGHT - 18, "单个行动最多 6VP", size: 14, color: ORANGE, weight: 700, anchor: "end")
svg << %(</svg>)

File.write(OUTPUT, svg.join("\n"))
puts OUTPUT
