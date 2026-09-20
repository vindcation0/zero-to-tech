import { useState, useEffect } from "react";

// 文字实验室的"输入区"卡片：
// 接收 onAnalyze 回调和 loading 状态，点击“开始分析”时将文本通知父组件发起请求
export default function InputCard({ onAnalyze, loading, initialText }) {
  const [text, setText] = useState("今天的风很轻，适合把脑海里的想法慢慢写下来。");

  useEffect(() => {
    if (initialText !== undefined && initialText !== null) {
      setText(initialText);
    }
  }, [initialText]);

  const handleSubmit = (e) => {
    e.preventDefault();
    if (onAnalyze && !loading) {
      onAnalyze(text);
    }
  };

  return (
    <article className="panel panel-half lab-panel card">
      <div className="panel-heading">
        <p className="section-kicker">输入区</p>
        <h3>贴一段中文</h3>
      </div>
      <form className="lab-form" onSubmit={handleSubmit}>
        <label htmlFor="text-input">文本内容</label>
        <textarea
          id="text-input"
          rows="8"
          placeholder="例如：生活没有标准答案，但每一天都值得认真感受。"
          value={text}
          onChange={(e) => setText(e.target.value)}
          disabled={loading}
        />
        {/* state 现身：text 一变，这行数字自动跟着变 */}
        <p className="lab-count">已输入 {text.length} 字</p>
        <button
          className="primary-button"
          type="submit"
          disabled={loading}
          style={{ opacity: loading ? 0.7 : 1, cursor: loading ? "not-allowed" : "pointer" }}
        >
          {loading ? "AI 分析中..." : "开始分析"}
        </button>
      </form>
    </article>
  );
}
