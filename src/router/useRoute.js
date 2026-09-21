import { useState, useEffect } from "react";

// 自定义路由 Hook：同步浏览器的地址栏路径
export function useRoute() {
  const [path, setPath] = useState(
    typeof window !== "undefined" ? window.location.pathname : "/"
  );

  useEffect(() => {
    // 监听浏览器的前进、后退按钮
    const handlePopState = () => {
      setPath(window.location.pathname);
    };

    window.addEventListener("popstate", handlePopState);
    return () => window.removeEventListener("popstate", handlePopState);
  }, []);

  // 编程式导航：修改地址栏并不触发页面整页刷新
  const navigate = (to) => {
    if (window.location.pathname !== to) {
      window.history.pushState({}, "", to);
      setPath(to);
    }
  };

  return { path, navigate };
}
