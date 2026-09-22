import React, { useState, useEffect, useCallback } from 'react';
import { X, ChevronLeft, ChevronRight, ZoomIn, ZoomOut } from 'lucide-react';
import { cn } from '../../lib/utils';

interface ImageLightboxProps {
  images: string[];
  initialIndex?: number;
  onClose: () => void;
}

const ZOOM_LEVELS = [1, 1.25, 1.5, 2, 2.5, 3];

export function ImageLightbox({ images, initialIndex = 0, onClose }: ImageLightboxProps) {
  const [currentIndex, setCurrentIndex] = useState(initialIndex);
  const [zoomIndex, setZoomIndex] = useState(0);
  const [pan, setPan] = useState({ x: 0, y: 0 });
  const [isDragging, setIsDragging] = useState(false);
  const [dragStart, setDragStart] = useState({ x: 0, y: 0 });

  const scale = ZOOM_LEVELS[zoomIndex];
  const isZoomed = scale > 1;

  // Prevent background scrolling
  useEffect(() => {
    const originalStyle = window.getComputedStyle(document.body).overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = originalStyle;
    };
  }, []);

  const handlePrev = useCallback(() => {
    if (currentIndex > 0) {
      setCurrentIndex(prev => prev - 1);
      setZoomIndex(0);
      setPan({ x: 0, y: 0 });
    }
  }, [currentIndex]);

  const handleNext = useCallback(() => {
    if (currentIndex < images.length - 1) {
      setCurrentIndex(prev => prev + 1);
      setZoomIndex(0);
      setPan({ x: 0, y: 0 });
    }
  }, [currentIndex, images.length]);

  const handleZoomIn = useCallback(() => {
    setZoomIndex(prev => Math.min(prev + 1, ZOOM_LEVELS.length - 1));
  }, []);

  const handleZoomOut = useCallback(() => {
    setZoomIndex(prev => {
      const next = Math.max(prev - 1, 0);
      if (next === 0) setPan({ x: 0, y: 0 });
      return next;
    });
  }, []);

  const handleResetZoom = useCallback(() => {
    setZoomIndex(0);
    setPan({ x: 0, y: 0 });
  }, []);

  // Keyboard navigation
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      switch (e.key) {
        case 'Escape':
          onClose();
          break;
        case 'ArrowLeft':
          handlePrev();
          break;
        case 'ArrowRight':
          handleNext();
          break;
        case '+':
        case '=':
          handleZoomIn();
          break;
        case '-':
          handleZoomOut();
          break;
        case '0':
          handleResetZoom();
          break;
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [onClose, handlePrev, handleNext, handleZoomIn, handleZoomOut, handleResetZoom]);

  // Dragging logic
  const handlePointerDown = (e: React.PointerEvent) => {
    if (isZoomed) {
      setIsDragging(true);
      setDragStart({ x: e.clientX - pan.x, y: e.clientY - pan.y });
      // Only capture pointer if it's the main container (prevent interfering with buttons)
      if (e.target instanceof Element && e.target.tagName !== 'BUTTON') {
        (e.target as Element).setPointerCapture(e.pointerId);
      }
    }
  };

  const handlePointerMove = (e: React.PointerEvent) => {
    if (isDragging && isZoomed) {
      setPan({
        x: e.clientX - dragStart.x,
        y: e.clientY - dragStart.y
      });
    }
  };

  const handlePointerUp = (e: React.PointerEvent) => {
    setIsDragging(false);
    if (e.target instanceof Element && e.target.hasPointerCapture(e.pointerId)) {
      (e.target as Element).releasePointerCapture(e.pointerId);
    }
  };

  // Simple swipe logic for non-zoomed state (touch only)
  const [touchStart, setTouchStart] = useState<number | null>(null);

  const handleTouchStart = (e: React.TouchEvent) => {
    if (!isZoomed && e.touches.length === 1) {
      setTouchStart(e.touches[0].clientX);
    }
  };

  const handleTouchEnd = (e: React.TouchEvent) => {
    if (!isZoomed && touchStart !== null && e.changedTouches.length === 1) {
      const touchEnd = e.changedTouches[0].clientX;
      const distance = touchStart - touchEnd;
      if (distance > 50) handleNext(); // swipe left
      if (distance < -50) handlePrev(); // swipe right
      setTouchStart(null);
    }
  };

  if (!images || images.length === 0) return null;

  return (
    <div
      className="fixed inset-0 z-[100] flex flex-col bg-black/95 select-none"
      role="dialog"
      aria-modal="true"
      aria-label="Image viewer"
    >
      {/* Header Toolbar */}
      <div className="absolute top-0 inset-x-0 z-10 flex items-center justify-between p-4 bg-gradient-to-b from-black/60 to-transparent">
        <div className="text-white/80 font-mono text-sm tracking-wider">
          {currentIndex + 1} / {images.length}
        </div>

        <div className="flex items-center gap-4">
          <div className="flex items-center gap-1 bg-black/40 rounded-md border border-white/10 p-1">
            <button
              onClick={handleZoomOut}
              disabled={zoomIndex === 0}
              className="p-1.5 rounded-sm text-white/70 hover:text-white hover:bg-white/10 disabled:opacity-30 disabled:hover:bg-transparent"
              aria-label="Zoom out"
            >
              <ZoomOut className="w-4 h-4" />
            </button>
            <button
              onClick={handleResetZoom}
              disabled={zoomIndex === 0}
              className="px-2 py-1 text-xs font-mono rounded-sm text-white/70 hover:text-white hover:bg-white/10 disabled:opacity-30 disabled:hover:bg-transparent"
              aria-label="Reset zoom"
            >
              {Math.round(scale * 100)}%
            </button>
            <button
              onClick={handleZoomIn}
              disabled={zoomIndex === ZOOM_LEVELS.length - 1}
              className="p-1.5 rounded-sm text-white/70 hover:text-white hover:bg-white/10 disabled:opacity-30 disabled:hover:bg-transparent"
              aria-label="Zoom in"
            >
              <ZoomIn className="w-4 h-4" />
            </button>
          </div>

          <button
            onClick={onClose}
            className="p-2 rounded-full bg-black/40 text-white/80 hover:text-white hover:bg-white/20 transition-colors"
            aria-label="Close image viewer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>
      </div>

      {/* Main Image Area */}
      <div
        className={cn(
          "flex-1 relative flex items-center justify-center overflow-hidden touch-none",
          isZoomed ? "cursor-grab active:cursor-grabbing" : ""
        )}
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        onPointerUp={handlePointerUp}
        onPointerCancel={handlePointerUp}
        onPointerLeave={handlePointerUp}
        onTouchStart={handleTouchStart}
        onTouchEnd={handleTouchEnd}
        // Click outside image to close
        onClick={(e) => {
          if (e.target === e.currentTarget && !isDragging) {
            onClose();
          }
        }}
      >
        <img
          src={images[currentIndex]}
          alt={`Incident media ${currentIndex + 1}`}
          className="max-w-full max-h-full object-contain pointer-events-none transition-transform duration-200 ease-out"
          style={{
            transform: `translate(${pan.x}px, ${pan.y}px) scale(${scale})`,
            transitionDuration: isDragging ? '0ms' : '200ms' // disable transition during drag for smoothness
          }}
          onError={(e) => {
            (e.target as HTMLImageElement).src = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="100%" height="100%"><rect width="100%" height="100%" fill="%23222"/><text x="50%" y="50%" fill="%23888" font-family="sans-serif" font-size="14" text-anchor="middle" dy=".3em">Failed to load image</text></svg>';
          }}
        />

        {/* Navigation Arrows */}
        {currentIndex > 0 && (
          <button
            onClick={(e) => { e.stopPropagation(); handlePrev(); }}
            className="absolute left-4 p-3 rounded-full bg-black/40 text-white/80 hover:text-white hover:bg-white/20 transition-colors z-10 hidden sm:block"
            aria-label="Previous image"
          >
            <ChevronLeft className="w-6 h-6" />
          </button>
        )}

        {currentIndex < images.length - 1 && (
          <button
            onClick={(e) => { e.stopPropagation(); handleNext(); }}
            className="absolute right-4 p-3 rounded-full bg-black/40 text-white/80 hover:text-white hover:bg-white/20 transition-colors z-10 hidden sm:block"
            aria-label="Next image"
          >
            <ChevronRight className="w-6 h-6" />
          </button>
        )}
      </div>

      {/* Thumbnails Strip */}
      {images.length > 1 && (
        <div className="h-24 bg-black/80 flex items-center justify-center p-2 border-t border-white/10 shrink-0 overflow-x-auto">
          <div className="flex gap-2 min-w-max px-2">
            {images.map((url, idx) => (
              <button
                key={idx}
                onClick={() => {
                  setCurrentIndex(idx);
                  setZoomIndex(0);
                  setPan({ x: 0, y: 0 });
                }}
                className={cn(
                  "relative w-16 h-16 rounded overflow-hidden shrink-0 transition-opacity",
                  currentIndex === idx ? "ring-2 ring-primary opacity-100" : "opacity-50 hover:opacity-100"
                )}
                aria-label={`View image ${idx + 1}`}
              >
                <img
                  src={url}
                  alt=""
                  className="w-full h-full object-cover"
                />
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
