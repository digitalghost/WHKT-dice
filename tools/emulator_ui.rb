#!/usr/bin/env ruby
# Small UI smoke-test driver; only touches the explicitly selected emulator.
require "open3"
require "rexml/document"
serial, operation, query, value = ARGV
abort "Emulator required" unless serial&.match?(/\Aemulator-\d+\z/)
adb = File.expand_path("../android-sdk/platform-tools/adb", __dir__)
def command(adb, serial, *args)
  out, err, status = Open3.capture3(adb, "-s", serial, *args)
  abort err unless status.success?
  out
end
command(adb, serial, "shell", "uiautomator", "dump", "/sdcard/whkt-ui.xml")
doc = REXML::Document.new(command(adb, serial, "shell", "cat", "/sdcard/whkt-ui.xml"))
nodes = REXML::XPath.match(doc, "//node")
if operation == "dump"
  nodes.each { |n| puts "#{n.attributes['text']} #{n.attributes['bounds']}" unless n.attributes["text"].to_s.empty? }
  exit
end
node = nodes.find { |n| n.attributes["text"] == query || n.attributes["content-desc"] == query }
abort "Missing UI text: #{query}" unless node
if operation == "fill"
  node = node.parent.elements.find { |n| n.attributes["class"] == "android.widget.EditText" }
  abort "Missing input" unless node
end
bounds = node.attributes["bounds"].scan(/\d+/).map(&:to_i)
command(adb, serial, "shell", "input", "tap", ((bounds[0] + bounds[2]) / 2).to_s, ((bounds[1] + bounds[3]) / 2).to_s)
if operation == "fill"
  command(adb, serial, "shell", "input", "keyevent", "KEYCODE_MOVE_END")
  60.times { command(adb, serial, "shell", "input", "keyevent", "KEYCODE_DEL") } unless node.attributes["text"].to_s.empty?
  abort "Smoke input is ASCII-only" unless value.ascii_only? && value.match?(/\A[a-zA-Z0-9_-]+\z/)
  command(adb, serial, "shell", "input", "text", value)
  command(adb, serial, "shell", "input", "keyevent", "KEYCODE_BACK")
end
