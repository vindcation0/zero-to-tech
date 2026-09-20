import { useState } from "react";
import Nav from "./Nav.jsx";
import PageHeading from "./PageHeading.jsx";
import AnimatedCardGrid from "./AnimatedCardGrid.jsx";
import InputCard from "./InputCard.jsx";
import ResultCard from "./ResultCard.jsx";
import { textLab } from "../data/site.js";

export default function TextLabPage({ current, onNavigate }) {
  // 维护结果数据状态（初始有默认值）
  const [result, setResult] = useState({
    text: "今天的风很轻，适合把脑海里的想法慢慢写下来。",
    pinyin: "jīn tiān de fēng hěn qīng …",
    score: 0.86,
    sentiment: "偏积极",
  });
  const [loading, setLoading] = useState(false);

  // 触发后端 Spring AI 分析
  const handleAnalyze = async (text) => {
    if (!text || !text.trim()) {
      alert("请输入需要分析的中文文本");
      return;
    }
    setLoading(true);
    try {
      const res = await fetch("/api/analyze", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ text: text.trim() }),
      });

      if (!res.ok) {
        throw new Error(`后端接口返回异常 HTTP ${res.status}`);
      }

      const data = await res.json();
      setResult(data);
    } catch (err) {
      console.error("AI 分析失败:", err);
      alert(`分析请求失败: ${err.message}\n请确保 Spring AI 后端服务已启动并在 8080 端口监听。`);
    } finally {
      setLoading(false);
    }
  };

  return (
    <AnimatedCardGrid className="dashboard-grid">
      <article className="hero-stage panel-full">
        <Nav current={current} onNavigate={onNavigate} />
        <PageHeading title={textLab.heroTitle} subtitle={textLab.heroSubtitle} />
      </article>

      <InputCard onAnalyze={handleAnalyze} loading={loading} />
      <ResultCard result={result} loading={loading} />
    </AnimatedCardGrid>
  );
}
