#!/usr/bin/env bash
# 杀戮小队批量整理：卡片提取之后的全部阶段，按 4 片并行执行。
set -uo pipefail
cd "$(dirname "$0")/.."

SHARDS=${SHARDS:-4}
STAGES=${STAGES:-"retitle ids en icons apply docs verify"}

run_stage () {
  local stage="$1"
  local pids=()
  for i in $(seq 0 $((SHARDS - 1))); do
    python3 tools/batch_kill_team_pipeline.py "$stage" --shard "$i/$SHARDS" \
      > "tmp/pdfs/stage_${stage}_${i}.log" 2>&1 &
    pids+=($!)
  done
  local fail=0
  for pid in "${pids[@]}"; do
    wait "$pid" || fail=1
  done
  if [ "$fail" -ne 0 ]; then
    echo "!! 阶段 $stage 有分片失败，见 tmp/pdfs/stage_${stage}_*.log"
  else
    echo "== 阶段 $stage 完成"
  fi
}

for stage in $STAGES; do
  echo ">>> $stage 开始 $(date '+%H:%M:%S')"
  run_stage "$stage"
  echo ">>> $stage 结束 $(date '+%H:%M:%S')"
done
echo "全部阶段结束 $(date '+%H:%M:%S')"
