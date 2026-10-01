#!/usr/bin/env ruby
# Preview-only rosters. Refuses physical devices or overwriting existing saved rosters.
require "json"
require "open3"
require "tmpdir"
require "cgi"
require "rexml/document"

serial = ARGV.fetch(0)
abort "Emulator serial required" unless serial.match?(/\Aemulator-\d+\z/)
adb = File.expand_path("../android-sdk/platform-tools/adb", __dir__)
package = "com.example.helloworld"
_, _, exists = Open3.capture3(adb, "-s", serial, "shell", "run-as", package, "test", "-f", "shared_prefs/saved_rosters.xml")
if exists.success?
  existing, _, status = Open3.capture3(adb, "-s", serial, "shell", "run-as", package, "cat", "shared_prefs/saved_rosters.xml")
  abort "Cannot inspect existing rosters" unless status.success?
  doc = REXML::Document.new(existing)
  node = REXML::XPath.first(doc, "/map/string[@name='rosters_v2']")
  abort "Existing roster data preserved; fixture not installed" unless node && JSON.parse(node.text || "[]").empty?
end

source = File.read(File.expand_path("../app/src/main/java/com/example/helloworld/RosterModels.kt", __dir__))
defaults = {}
source.scan(/OperativeTemplate\(\s*"([^"]+)".*?listOf\((.*?)\),\s*listOf\((.*?)\)\s*\)/m) do |id, _, weapons|
  defaults[id] = weapons.scan(/"([^"]+)"/).flatten
end
teams = [
  ["preview-a", "IMP-AOD", "演示 · 死亡天使", %w[aod_sergeant aod_warrior aod_warrior aod_assault_warrior aod_grenadier aod_gunner]],
  ["preview-b", "CHAOS-PM", "演示 · 瘟疫战士", %w[pm_champion pm_bombardier pm_fighter pm_heavy_gunner pm_icon_bearer pm_plaguecaster]]
]
rosters = teams.map do |id, team, name, members|
  { rosterId: id, teamId: team, name: name, updatedAt: (Time.now.to_f * 1000).to_i,
    members: members.each_with_index.map { |op, i|
      { id: "#{id}-#{i}", operativeId: op, callsign: "", weaponIds: defaults.fetch(op), customAvatarUri: "" }
    } }
end
Dir.mktmpdir("whkt-preview-") do |dir|
  xml = File.join(dir, "saved_rosters.xml")
  File.write(xml, '<?xml version="1.0" encoding="utf-8"?><map><string name="rosters_v2">' +
    CGI.escapeHTML(JSON.generate(rosters)) + '</string></map>')
  system(adb, "-s", serial, "shell", "am", "force-stop", package, exception: true)
  system(adb, "-s", serial, "push", xml, "/data/local/tmp/whkt-preview-rosters.xml", exception: true)
  system(adb, "-s", serial, "shell", "run-as", package, "mkdir", "-p", "shared_prefs", exception: true)
  system(adb, "-s", serial, "shell", "run-as", package, "cp", "/data/local/tmp/whkt-preview-rosters.xml", "shared_prefs/saved_rosters.xml", exception: true)
  system(adb, "-s", serial, "shell", "am", "start", "-n", "#{package}/.BattleHubActivity", exception: true)
end
