"use client";

// Pretty-print JSON (ensure multi-line), then syntax-highlight
export function SyntaxHighlight({ json }: { json: string }) {
  let formatted = json;
  try {
    formatted = JSON.stringify(JSON.parse(json), null, 2);
  } catch { /* keep raw if invalid */ }
  const lines = formatted.split("\n");
  return (
    <div className="rounded bg-[#0d1117] text-[12px] overflow-x-auto">
      <table className="w-full" aria-label="Page Table">
        <tbody>
          {lines.map((line, i) => {
            const highlighted = line
              .replace(/("(?:[^"\\]|\\.)*")\s*:/g, '<span class="text-[#79c0ff]">$1</span>:')
              .replace(/:\s*("(?:[^"\\]|\\.)*")/g, ': <span class="text-[#a5d6ff]">$1</span>')
              .replace(/:\s*(true|false)/g, ': <span class="text-[#ff7b72]">$1</span>')
              .replace(/:\s*(null)/g, ': <span class="text-[#ff7b72]">$1</span>')
              .replace(/:\s*(-?\d+(?:\.\d+)?)/g, ': <span class="text-[#79c0ff]">$1</span>');
            return (
              <tr key={i} className="leading-5">
                <td className="pr-3 text-right text-[#484f58] select-none w-7 shrink-0 pl-2">{i + 1}</td>
                <td className="text-[#c9d1d9] whitespace-pre" dangerouslySetInnerHTML={{ __html: highlighted || "&nbsp;" }} />
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
