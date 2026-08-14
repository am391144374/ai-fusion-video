"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { CircleStop, ImageIcon, Loader2, Video } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { aiModelApi, type AiModel } from "@/lib/api/ai-model";
import type { AssetItem } from "@/lib/api/asset";
import { generationApi } from "@/lib/api/generation";
import { storyboardApi, type StoryboardItem } from "@/lib/api/storyboard";
import { cn } from "@/lib/utils";

type ReferenceMode = "none" | "first" | "last" | "firstLast" | "asset";
type QueueStatus = "waiting" | "generating" | "success" | "failed" | "paused" | "stopped";

interface QueueItem {
  item: StoryboardItem;
  referenceMode: ReferenceMode;
  assetImageUrl?: string;
  status: QueueStatus;
  errorMessage?: string;
}

interface DirectVideoGenerationDialogProps {
  open: boolean;
  projectId: number;
  items: StoryboardItem[];
  assetItems: AssetItem[];
  onClose: () => void;
  onCompleted: () => void;
}

const referenceOptions = [
  { value: "none", label: "不使用参考图" },
  { value: "first", label: "使用首帧" },
  { value: "last", label: "使用尾帧" },
  { value: "firstLast", label: "使用首尾帧" },
  { value: "asset", label: "使用资产/分镜图" },
] satisfies { value: ReferenceMode; label: string }[];

const statusConfig: Record<QueueStatus, { label: string; className: string }> = {
  waiting: { label: "等待中", className: "text-muted-foreground" },
  generating: { label: "生成中", className: "text-violet-500" },
  success: { label: "已成功", className: "text-emerald-500" },
  failed: { label: "失败", className: "text-destructive" },
  paused: { label: "已暂停", className: "text-amber-500" },
  stopped: { label: "已停止", className: "text-amber-500" },
};

const sleep = (milliseconds: number) => new Promise<void>((resolve) => window.setTimeout(resolve, milliseconds));

function parseIds(raw: string | null): number[] {
  if (!raw) return [];
  try {
    const ids = JSON.parse(raw);
    return Array.isArray(ids) ? ids.filter((id): id is number => typeof id === "number") : [];
  } catch {
    return [];
  }
}

/**
 * 直接生成视频弹窗。
 * 批量任务严格由前端按顺序处理，确保前一条成功后才会提交下一条。
 */
export function DirectVideoGenerationDialog({
  open,
  projectId,
  items,
  assetItems,
  onClose,
  onCompleted,
}: DirectVideoGenerationDialogProps) {
  const [models, setModels] = useState<AiModel[]>([]);
  const [modelId, setModelId] = useState<number | null>(null);
  const [loadingModels, setLoadingModels] = useState(false);
  const [queueItems, setQueueItems] = useState<QueueItem[]>([]);
  const [running, setRunning] = useState(false);
  const [started, setStarted] = useState(false);
  const [stopRequested, setStopRequested] = useState(false);
  const stopRequestedRef = useRef(false);
  const initializedItemIdsRef = useRef<string | null>(null);

  const modelOptions = useMemo(
    () => models.map((model) => ({ value: model.id, label: model.name || model.code })),
    [models],
  );
  const itemIds = items.map((item) => item.id).join(",");

  // 离开当前页面后不再提交新的分镜任务；已提交的当前视频仍由后端继续完成。
  useEffect(() => () => {
    stopRequestedRef.current = true;
  }, []);

  // 步骤一：每次打开新的任务选择弹窗时，加载可用的视频模型并初始化每条分镜的参考图选择。
  useEffect(() => {
    if (!open) {
      initializedItemIdsRef.current = null;
      return;
    }
    if (initializedItemIdsRef.current === itemIds) return;

    initializedItemIdsRef.current = itemIds;
    setQueueItems(items.map((item) => ({ item, referenceMode: "none", status: "waiting" })));
    setStarted(false);
    setStopRequested(false);
    stopRequestedRef.current = false;
    setLoadingModels(true);
    void aiModelApi.listByType(3)
      .then((videoModels) => {
        setModels(videoModels);
        setModelId(videoModels.find((model) => model.defaultModel)?.id ?? videoModels[0]?.id ?? null);
      })
      .catch((error) => {
        toast.error(error instanceof Error ? error.message : "加载视频模型失败");
      })
      .finally(() => setLoadingModels(false));
  }, [itemIds, items, open]);

  const updateQueueItem = (itemId: number, patch: Partial<QueueItem>) => {
    setQueueItems((current) => current.map((entry) => (
      entry.item.id === itemId ? { ...entry, ...patch } : entry
    )));
  };

  const markRemaining = (status: "paused" | "stopped", afterItemId?: number) => {
    let canStop = afterItemId === undefined;
    setQueueItems((current) => current.map((entry) => {
      if (entry.item.id === afterItemId) {
        canStop = true;
        return entry;
      }
      if (canStop && entry.status === "waiting") {
        return { ...entry, status };
      }
      return entry;
    }));
  };

  const resolveReference = (entry: QueueItem) => {
    const { item, referenceMode } = entry;

    if (referenceMode === "none") return {};
    if (referenceMode === "first") {
      if (!item.firstFrameImageUrl) throw new Error("未设置首帧图");
      return { firstFrameImageUrl: item.firstFrameImageUrl };
    }
    if (referenceMode === "last") {
      if (!item.lastFrameImageUrl) throw new Error("未设置尾帧图");
      return { lastFrameImageUrl: item.lastFrameImageUrl };
    }
    if (referenceMode === "firstLast") {
      if (!item.firstFrameImageUrl || !item.lastFrameImageUrl) throw new Error("未同时设置首帧图和尾帧图");
      return {
        firstFrameImageUrl: item.firstFrameImageUrl,
        lastFrameImageUrl: item.lastFrameImageUrl,
      };
    }
    if (!entry.assetImageUrl) throw new Error("请选择资产/分镜图");
    return { referenceImageUrls: JSON.stringify([entry.assetImageUrl]) };
  };

  const getAssetImageOptions = (item: StoryboardItem) => {
    const assetItemIds = [
      ...parseIds(item.characterIds),
      ...(item.sceneAssetItemId ? [item.sceneAssetItemId] : []),
      ...parseIds(item.propIds),
    ];
    const options = assetItems
      .filter((assetItem) => assetItemIds.includes(assetItem.id) && assetItem.imageUrl)
      .map((assetItem) => ({ value: assetItem.imageUrl!, label: assetItem.name || `资产图 #${assetItem.id}` }));
    const storyboardImage = item.generatedImageUrl || item.imageUrl || item.referenceImageUrl;
    if (storyboardImage) options.unshift({ value: storyboardImage, label: "当前分镜图" });
    return options;
  };

  const waitForVideo = async (taskId: string) => {
    // 步骤二：任务已提交后只轮询既有任务状态接口，不占用提交请求连接。
    while (true) {
      await sleep(3000);
      const task = await generationApi.getVideoTask(taskId);
      if (task.status === 2) {
        const generatedItems = await generationApi.listVideoItems(task.id);
        const videoUrl = generatedItems.find((item) => item.status === 1 && item.videoUrl)?.videoUrl;
        if (!videoUrl) throw new Error("视频任务已完成但未返回视频地址");
        return videoUrl;
      }
      if (task.status === 3) throw new Error(task.errorMsg || "视频生成失败");
    }
  };

  const handleStart = async () => {
    if (!modelId) {
      toast.error("请先选择视频模型");
      return;
    }
    if (queueItems.length === 0) return;

    // 步骤三：固定并发为 1，当前分镜成功并回填后才继续下一条。
    setStarted(true);
    setRunning(true);
    setStopRequested(false);
    stopRequestedRef.current = false;

    let failed = false;
    for (const entry of queueItems) {
      if (stopRequestedRef.current) {
        markRemaining("stopped");
        break;
      }
      if (failed) break;

      updateQueueItem(entry.item.id, { status: "generating", errorMessage: undefined });
      try {
        const prompt = entry.item.videoPrompt?.trim();
        if (!prompt) throw new Error("缺少视频提示词");
        const reference = resolveReference(entry);
        const taskId = await generationApi.submitVideo({
          projectId,
          prompt,
          modelId,
          duration: entry.item.duration ?? undefined,
          generateMode: Object.keys(reference).length > 0 ? "image2video" : "text2video",
          category: "storyboard_direct_video_generation",
          ...reference,
        });
        const videoUrl = await waitForVideo(taskId);

        // 步骤四：视频成功后复用分镜更新接口回填，字段与 Agent 最终落库字段保持一致。
        await storyboardApi.updateItem({
          id: entry.item.id,
          generatedVideoUrl: videoUrl,
          videoPrompt: prompt,
        });
        updateQueueItem(entry.item.id, { status: "success" });
      } catch (error) {
        const errorMessage = error instanceof Error ? error.message : "视频生成失败";
        updateQueueItem(entry.item.id, { status: "failed", errorMessage });
        markRemaining("paused", entry.item.id);
        failed = true;
        toast.error(`分镜 ${entry.item.id} 生成失败，已暂停后续任务`);
      }
    }

    setRunning(false);
    onCompleted();
  };

  const handleStop = () => {
    // 步骤五：只阻止后续提交，不取消当前已提交的视频任务。
    stopRequestedRef.current = true;
    setStopRequested(true);
  };

  const title = items.length === 1 ? "直接生成视频" : "批量直接生成视频";

  return (
    <Dialog
      open={open}
      onOpenChange={(nextOpen) => {
        if (!nextOpen && !running) onClose();
      }}
    >
      <DialogContent className="max-w-3xl max-h-[calc(100vh-2rem)] gap-4 overflow-hidden p-5" showCloseButton={!running}>
        <DialogHeader className="shrink-0">
          <DialogTitle className="flex items-center gap-2"><Video className="text-violet-500" />{title}</DialogTitle>
          <DialogDescription>
            {started ? "固定串行生成中；当前项成功后才会提交下一项。" : "统一选择模型，并为每个分镜选择需要使用的参考图。"}
          </DialogDescription>
        </DialogHeader>

        {!started ? (
          <div className="min-h-0 space-y-4 overflow-y-auto">
            <div className="rounded-lg border border-border/20 bg-background/70 p-3">
              <p className="mb-2 text-xs font-medium">视频模型</p>
              <Select
                value={modelId ?? undefined}
                onValueChange={(value) => setModelId(Number(value))}
                items={modelOptions}
                disabled={loadingModels || modelOptions.length === 0}
              >
                <SelectTrigger className="w-full text-sm"><SelectValue placeholder={loadingModels ? "加载模型中..." : "选择视频模型"} /></SelectTrigger>
                <SelectContent className="text-sm"><SelectGroup>{modelOptions.map((option) => <SelectItem key={option.value} value={option.value}>{option.label}</SelectItem>)}</SelectGroup></SelectContent>
              </Select>
              {!loadingModels && modelOptions.length === 0 && <p className="mt-2 text-xs text-destructive">暂无可用视频模型，请先在模型设置中启用。</p>}
            </div>

            <div className="space-y-2">
              {queueItems.map((entry) => (
                <div key={entry.item.id} className="rounded-lg border border-border/20 bg-background/70 p-3">
                  <div className="flex items-center justify-between gap-3">
                    <div className="min-w-0">
                      <p className="text-sm font-medium">分镜 ID：{entry.item.id}</p>
                      <p className="mt-1 line-clamp-2 text-xs text-muted-foreground">{entry.item.videoPrompt || "未填写视频提示词"}</p>
                    </div>
                    <span className="shrink-0 text-xs text-muted-foreground">{entry.item.duration ? `${entry.item.duration} 秒` : "未设置时长"}</span>
                  </div>
                  <div className="mt-3 flex items-center gap-2">
                    <ImageIcon className="size-4 text-muted-foreground" />
                    <Select
                      value={entry.referenceMode}
                      onValueChange={(value) => updateQueueItem(entry.item.id, { referenceMode: value as ReferenceMode })}
                      items={referenceOptions}
                    >
                      <SelectTrigger size="sm" className="w-full text-xs"><SelectValue placeholder="选择参考图" /></SelectTrigger>
                      <SelectContent className="text-xs"><SelectGroup>{referenceOptions.map((option) => <SelectItem key={option.value} value={option.value} className="text-xs">{option.label}</SelectItem>)}</SelectGroup></SelectContent>
                    </Select>
                  </div>
                  {entry.referenceMode === "asset" && (
                    <Select
                      value={entry.assetImageUrl}
                      onValueChange={(value) => updateQueueItem(entry.item.id, { assetImageUrl: String(value) })}
                      items={getAssetImageOptions(entry.item)}
                    >
                      <SelectTrigger size="sm" className="mt-2 w-full text-xs"><SelectValue placeholder="选择具体的资产/分镜图" /></SelectTrigger>
                      <SelectContent className="text-xs"><SelectGroup>{getAssetImageOptions(entry.item).map((option) => <SelectItem key={option.value} value={option.value} className="text-xs">{option.label}</SelectItem>)}</SelectGroup></SelectContent>
                    </Select>
                  )}
                </div>
              ))}
            </div>
          </div>
        ) : (
          <div className="min-h-0 space-y-2 overflow-y-auto">
            {queueItems.map((entry) => {
              const config = statusConfig[entry.status];
              return (
                <div key={entry.item.id} className="rounded-lg border border-border/20 bg-background/70 p-3">
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <p className="text-sm font-medium">分镜 ID：{entry.item.id}</p>
                      <p className="mt-1 line-clamp-2 text-xs text-muted-foreground">{entry.item.videoPrompt || "未填写视频提示词"}</p>
                      {entry.errorMessage && <p className="mt-1 text-xs text-destructive">{entry.errorMessage}</p>}
                    </div>
                    <div className="shrink-0 text-right">
                      <p className="text-xs text-muted-foreground">{entry.item.duration ? `${entry.item.duration} 秒` : "未设置时长"}</p>
                      <p className={cn("mt-1 text-xs font-medium", config.className)}>{config.label}</p>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}

        <DialogFooter className="shrink-0">
          {!started ? (
            <>
              <Button variant="outline" onClick={onClose}>取消</Button>
              <Button variant="video" onClick={() => void handleStart()} disabled={loadingModels || !modelId || queueItems.length === 0}><Video />开始生成</Button>
            </>
          ) : running ? (
            <>
              <p className="mr-auto flex items-center gap-1.5 text-xs text-muted-foreground"><Loader2 className="size-3.5 animate-spin" />{stopRequested ? "当前视频完成后将停止后续提交" : "正在严格串行生成"}</p>
              <Button variant="outline" onClick={handleStop} disabled={stopRequested}><CircleStop />停止后续</Button>
            </>
          ) : (
            <Button variant="outline" onClick={onClose}>关闭</Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
