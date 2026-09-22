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
  const [commentary, setCommentary] = useState(
    "这句话借轻柔的微风勾勒出宁静舒展的心境，文字间流淌着内省与诗意。表达者处于一种高度专注而松弛的精神状态，将思维具象化为可以“慢慢写下”的文字，展现出积极、安详的生活态度。"
  );
  const [loading, setLoading] = useState(false);
  const [isStreaming, setIsStreaming] = useState(false);
  const [history, setHistory] = useState([]);
  const [inputText, setInputText] = useState("");

  // 获取历史记录 (携带 Cookie 凭证)
  const fetchHistory = async () => {
    try {
      const res = await fetch("/api/history", {
        credentials: "include",
      });
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

  // 触发后端 Spring AI SSE 流式分析 (携带 Cookie 凭证)
  const handleAnalyze = async (text) => {
    if (!text || !text.trim()) {
      alert("请输入需要分析的中文文本");
      return;
    }
    setLoading(true);
    setIsStreaming(true);
    setCommentary(""); // 清空旧的解读，准备开始打字机输出

    try {
      const response = await fetch("/api/analyze/stream", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({ text: text.trim() }),
      });

      if (!response.ok) {
        throw new Error(`后端接口返回异常 HTTP ${response.status}`);
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder("utf-8");
      let buffer = "";

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        // 按换行符分割处理 SSE 行
        const lines = buffer.split("\n");
        // 保留最后一个可能尚未完整的行到 buffer
        buffer = lines.pop() || "";

        for (const line of lines) {
          const trimmed = line.trim();
          if (!trimmed || !trimmed.startsWith("data:")) continue;

          const jsonStr = trimmed.slice(5).trim();
          if (!jsonStr) continue;

          try {
            const msg = JSON.parse(jsonStr);
            if (msg.type === "meta") {
              setResult({
                text: msg.text,
                pinyin: msg.pinyin,
                score: msg.score,
                sentiment: msg.sentiment,
              });
            } else if (msg.type === "chunk") {
              if (msg.content) {
                setCommentary((prev) => prev + msg.content);
              }
            } else if (msg.type === "done") {
              setIsStreaming(false);
              // 流结束，刷新历史记录
              fetchHistory();
            } else if (msg.type === "error") {
              console.error("流式错误帧:", msg.message);
              alert(`分析异常: ${msg.message}`);
              setIsStreaming(false);
            }
          } catch (e) {
            console.warn("解析 SSE 帧失败:", jsonStr, e);
          }
        }
      }
    } catch (err) {
      console.error("AI 分析失败:", err);
      alert(`分析请求失败: ${err.message}\n请确保后端服务已启动且网络正常。`);
    } finally {
      setLoading(false);
      setIsStreaming(false);
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
    setCommentary(`【历史记录】情绪评分：${Number(item.score).toFixed(2)}，标签：${item.sentiment}。原文：“${item.text}”`);
    setIsStreaming(false);
    setInputText(item.text);
  };

  // 删除某条历史记录 (携带 Cookie 凭证)
  const handleDeleteHistory = async (id) => {
    try {
      const res = await fetch(`/api/history/${id}`, {
        method: "DELETE",
        credentials: "include",
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
      <ResultCard result={result} commentary={commentary} isStreaming={isStreaming} loading={loading} />
      <HistoryCard history={history} onSelect={handleSelectHistory} onDelete={handleDeleteHistory} />
    </AnimatedCardGrid>
  );
}
