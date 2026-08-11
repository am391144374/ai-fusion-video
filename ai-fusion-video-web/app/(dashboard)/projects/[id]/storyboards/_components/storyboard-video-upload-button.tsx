"use client";

import { Loader2, Upload } from "lucide-react";
import { useRef, useState } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";

interface StoryboardVideoUploadButtonProps {
  itemId: number;
  className?: string;
  onUpload: (itemId: number, file: File) => Promise<void>;
}

/** 分镜视频上传按钮：仅选择 MP4 并把实际保存交给分镜页面统一处理。 */
export function StoryboardVideoUploadButton({
  itemId,
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
      <Button
        type="button"
        variant="ghost"
        size="icon-sm"
        className={className}
        disabled={uploading}
        onClick={(event) => {
          event.stopPropagation();
          fileInputRef.current?.click();
        }}
        aria-label="上传 MP4 视频"
        title="上传 MP4 视频"
      >
        {uploading ? <Loader2 className="animate-spin" /> : <Upload />}
      </Button>
    </>
  );
}
