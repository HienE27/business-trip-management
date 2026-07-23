"use client";

interface IterationSliderProps {
  currentIndex: number;
  totalFrames: number;
  onSeek: (index: number) => void;
}

/**
 * Iteration slider for seeking to specific frames.
 */
export function IterationSlider({ currentIndex, totalFrames, onSeek }: IterationSliderProps) {
  const progress = totalFrames > 0 ? (currentIndex / (totalFrames - 1)) * 100 : 0;

  return (
    <div className="bg-surface-container-lowest border border-outline-variant rounded-xl p-4">
      <div className="flex items-center gap-4">
        {/* Label */}
        <div className="shrink-0 w-24">
          <div className="text-label-sm text-on-surface-variant">Iteration</div>
          <div className="font-headline-md text-headline-md text-on-surface">
            {currentIndex}
          </div>
        </div>

        {/* Slider */}
        <div className="flex-1 relative">
          <input
            type="range"
            min="0"
            max={totalFrames - 1}
            value={currentIndex}
            onChange={(e) => onSeek(parseInt(e.target.value))}
            className="w-full h-2 bg-surface-variant rounded-full appearance-none cursor-pointer
                       [&::-webkit-slider-thumb]:appearance-none [&::-webkit-slider-thumb]:w-4 [&::-webkit-slider-thumb]:h-4
                       [&::-webkit-slider-thumb]:rounded-full [&::-webkit-slider-thumb]:bg-primary
                       [&::-webkit-slider-thumb]:cursor-pointer [&::-webkit-slider-thumb]:transition-transform
                       [&::-webkit-slider-thumb]:hover:scale-110
                       [&::-moz-range-thumb]:w-4 [&::-moz-range-thumb]:h-4 [&::-moz-range-thumb]:rounded-full
                       [&::-moz-range-thumb]:bg-primary [&::-moz-range-thumb]:border-0 [&::-moz-range-thumb]:cursor-pointer"
          />

          {/* Progress bar */}
          <div
            className="absolute top-1/2 left-0 h-2 bg-primary rounded-full -translate-y-1/2 pointer-events-none transition-all"
            style={{ width: `${progress}%` }}
          />
        </div>

        {/* Total */}
        <div className="shrink-0 w-24 text-right">
          <div className="text-label-sm text-on-surface-variant">Total</div>
          <div className="font-headline-md text-headline-md text-on-surface">
            {totalFrames}
          </div>
        </div>
      </div>
    </div>
  );
}
