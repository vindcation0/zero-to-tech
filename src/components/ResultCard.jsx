import { useEffect, useRef } from "react";
import { animate, scrambleText } from "animejs";

// 分析结果卡片：展示后端返回的原文、拼音、情感分数、情绪判断与流式深度赏析
export default function ResultCard({
  result,
  commentary,
  isStreaming,
  loading,
}) {
  const cardRef = useRef(null);
  const scoreRef = useRef(null);

  // 1. 卡片初始淡入入场
  useEffect(() => {
    if (cardRef.current) {
      animate(cardRef.current, {
        opacity: [0, 1],
        translateY: [24, 0],
        duration: 700,
        ease: "outBack",
      });
    }
  }, []);

  // 2. 当情感分数更新时，触发数字滚动归位动画
  useEffect(() => {
    if (scoreRef.current && result?.score !== undefined) {
      // 格式化为保留两位小数的字符串
      const scoreStr = Number(result.score).toFixed(2);
      scoreRef.current.textContent = scoreStr;
      animate(scoreRef.current, {
        innerHTML: scrambleText({ chars: "0-9" }),
        duration: 1200,
      });
    }
  }, [result?.score]);

  return (
    <article ref={cardRef} className="panel panel-half lab-panel result-panel card">
      <div className="panel-heading">
        <p className="section-kicker">结果区</p>
        <h3>{loading && !isStreaming ? "AI 分析中，请稍候..." : "分析结果"}</h3>
      </div>
      <div className="result-stack" style={{ opacity: loading && !isStreaming ? 0.6 : 1, transition: "opacity 0.3s" }}>
        <div className="result-item">
          <span>原文</span>
          <p>{result?.text || "暂无内容"}</p>
        </div>
        <div className="result-item">
          <span>拼音</span>
          <p>{result?.pinyin || "…"}</p>
        </div>
        <div className="result-grid">
          <div className="result-badge">
            <span>情感分数</span>
            <strong data-score ref={scoreRef}>
              {result?.score !== undefined ? Number(result.score).toFixed(2) : "0.00"}
            </strong>
          </div>
          <div className="result-badge">
            <span>情感判断</span>
            <strong>{result?.sentiment || "未知"}</strong>
          </div>
        </div>

        {/* AI 深度语境赏析与实时打字机流式呈现 */}
        <div className="result-item commentary-box">
          <div className="commentary-header">
            <span>AI 深度语境赏析</span>
            {isStreaming && <span className="streaming-indicator">逐字推导中...</span>}
          </div>
          <p className="commentary-text">
            {commentary || (loading ? "AI 正在细细品读意境与情感..." : "暂无解读")}
            {isStreaming && <span className="typing-cursor">▌</span>}
          </p>
        </div>
      </div>
    </article>
  );
}
