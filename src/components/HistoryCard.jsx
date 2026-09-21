// 格式化时间为友好显示
function formatTime(timeStr) {
  if (!timeStr) return "";
  const date = new Date(timeStr);
  if (isNaN(date.getTime())) return timeStr;
  const now = new Date();
  const diffSec = Math.floor((now - date) / 1000);

  if (diffSec < 60) return "刚刚";
  if (diffSec < 3600) return `${Math.floor(diffSec / 60)} 分钟前`;
  if (diffSec < 86400) return `${Math.floor(diffSec / 3600)} 小时前`;

  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  const h = String(date.getHours()).padStart(2, "0");
  const min = String(date.getMinutes()).padStart(2, "0");
  return `${m}-${d} ${h}:${min}`;
}

export default function HistoryCard({ history = [], onSelect, onDelete }) {
  return (
    <article className="panel panel-full lab-panel history-panel card">
      <div className="panel-heading">
        <p className="section-kicker">数据持久化 · MySQL</p>
        <h3>分析历史记录 ({history.length})</h3>
      </div>

      {history.length === 0 ? (
        <p className="history-empty">暂无分析历史，在上方输入文字并点击“开始分析”即可自动保存到数据库中~</p>
      ) : (
        <div className="history-list">
          {history.map((item) => (
            <div
              key={item.id}
              className="history-row"
              onClick={() => onSelect && onSelect(item)}
              title="点击回显此记录到上方卡片"
            >
              <div className="history-main-info">
                <p className="history-text">{item.text}</p>
                <div className="history-meta">
                  <span>拼音: {item.pinyin}</span>
                  <span>•</span>
                  <span>{formatTime(item.createdAt)}</span>
                </div>
              </div>

              <div className="history-side-info">
                <span className="history-tag">{item.sentiment}</span>
                <span className="history-score">
                  {typeof item.score === "number" ? item.score.toFixed(2) : item.score}
                </span>
                <button
                  type="button"
                  className="history-delete-btn"
                  title="删除此条记录"
                  onClick={(e) => {
                    e.stopPropagation();
                    if (confirm("确定要删除这条分析记录吗？")) {
                      onDelete && onDelete(item.id);
                    }
                  }}
                >
                  ✕
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </article>
  );
}
