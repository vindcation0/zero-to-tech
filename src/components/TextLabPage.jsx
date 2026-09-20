import { useState, useEffect } from "react";
import Nav from "./Nav.jsx";
import PageHeading from "./PageHeading.jsx";
import AnimatedCardGrid from "./AnimatedCardGrid.jsx";
import InputCard from "./InputCard.jsx";
import ResultCard from "./ResultCard.jsx";
import HistoryCard from "./HistoryCard.jsx";
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
  const [history, setHistory] = useState([]);
  const [inputText, setInputText] = useState("");

  // 获取历史记录
  const fetchHistory = async () => {
    try {
      const res = await fetch("/api/history");
      if (res.ok) {
        const data = await res.json();
        setHistory(Array.isArray(data) ? data : []);
      }
    } catch (err) {
      console.warn("加载历史分析记录失败:", err.message);
    }
  };

  // 页面初次加载时拉取一次历史记录
  useEffect(() => {
    fetchHistory();
  }, []);

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
      // 分析成功后重新刷新历史记录
      fetchHistory();
    } catch (err) {
      console.error("AI 分析失败:", err);
      alert(`分析请求失败: ${err.message}\n请确保后端服务已启动且 MySQL 运行正常。`);
    } finally {
      setLoading(false);
    }
  };

  // 选中某条历史记录，回显到结果区和输入框
  const handleSelectHistory = (item) => {
    setResult({
      text: item.text,
      pinyin: item.pinyin,
      score: item.score,
      sentiment: item.sentiment,
    });
    setInputText(item.text);
  };

  // 删除某条历史记录
  const handleDeleteHistory = async (id) => {
    try {
      const res = await fetch(`/api/history/${id}`, {
        method: "DELETE",
      });
      if (res.ok) {
        setHistory((prev) => prev.filter((item) => item.id !== id));
      } else {
        alert("删除失败，请稍后重试");
      }
    } catch (err) {
      alert(`删除异常: ${err.message}`);
    }
  };

  return (
    <AnimatedCardGrid className="dashboard-grid">
      <article className="hero-stage panel-full">
        <Nav current={current} onNavigate={onNavigate} />
        <PageHeading title={textLab.heroTitle} subtitle={textLab.heroSubtitle} />
      </article>

      <InputCard onAnalyze={handleAnalyze} loading={loading} initialText={inputText} />
      <ResultCard result={result} loading={loading} />
      <HistoryCard history={history} onSelect={handleSelectHistory} onDelete={handleDeleteHistory} />
    </AnimatedCardGrid>
  );
}
