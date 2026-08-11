"use client";

import { Loader2, Upload } from "lucide-react";
import { useRef, useState } from "react";
import { toast } from "sonner";
import { cn } from "@/lib/utils";

interface StoryboardVideoUploadButtonProps {
  itemId: number;
  visualStyle: "table" | "card";
  className?: string;
  onUpload: (itemId: number, file: File) => Promise<void>;
}

/** 分镜视频上传按钮：仅选择 MP4，并复用各视图生成视频按钮的视觉样式。 */
export function StoryboardVideoUploadButton({
  itemId,
  visualStyle,
  className,
  onUpload,
}: StoryboardVideoUploadButtonProps) {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);

  // 步骤一：用户选择文件后先在浏览器侧拦截非 MP4，避免无效上传。
  const handleFileChange = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;

    const isMp4 = file.type === "video/mp4" || file.name.toLowerCase().endsWith(".mp4");
    if (!isMp4) {
      toast.error("仅支持 MP4 视频文件");
      return;
    }

    // 步骤二：上传和分镜保存是一个连续操作，完成前禁止重复选择文件。
    try {
      setUploading(true);
      await onUpload(itemId, file);
      toast.success("视频已保存到分镜");
    } catch (error) {
      console.error("上传分镜视频失败:", error);
      toast.error("上传视频失败，请重试");
    } finally {
      setUploading(false);
    }
  };

  return (
    <>
      <input
        ref={fileInputRef}
        type="file"
        accept="video/mp4,.mp4"
        className="hidden"
        onChange={(event) => void handleFileChange(event)}
      />
      <button
        type="button"
        className={cn(
          visualStyle === "card"
            ? "p-1.5 rounded-md bg-black/40 backdrop-blur-sm opacity-0 group-hover:opacity-100 transition-all hover:bg-purple-500/60 text-white/90"
            : "p-1 rounded opacity-0 group-hover:opacity-100 text-muted-foreground hover:text-purple-400 hover:bg-purple-500/10 transition-all",
          className
        )}
        disabled={uploading}
        onClick={(event) => {
          event.stopPropagation();
          fileInputRef.current?.click();
        }}
        aria-label="上传 MP4 视频"
        title="上传 MP4 视频"
      >
        {uploading ? (
          <Loader2 className={cn(visualStyle === "card" ? "h-3.5 w-3.5 animate-spin" : "h-3 w-3 animate-spin")} />
        ) : (
          <Upload className={visualStyle === "card" ? "h-3.5 w-3.5" : "h-3 w-3"} />
        )}
      </button>
    </>
  );
}
