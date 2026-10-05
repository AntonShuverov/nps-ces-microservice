/** Стили поп-апа. Живут внутри Shadow DOM и не пересекаются со стилями сайта. */
export const styles = `
:host { all: initial; }
* { box-sizing: border-box; }

.sw-overlay {
  position: fixed;
  inset: 0;
  z-index: 2147483000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  background: rgba(17, 24, 39, 0.45);
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Arial, sans-serif;
  color: #111827;
}

.sw-popup {
  position: relative;
  width: 100%;
  max-width: 560px;
  max-height: calc(100vh - 32px);
  overflow-y: auto;
  padding: 24px;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 20px 50px rgba(0, 0, 0, 0.2);
}

.sw-header { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; padding-right: 32px; }
.sw-title { margin: 0; font-size: 20px; font-weight: 600; }
.sw-progress { font-size: 14px; color: #6b7280; white-space: nowrap; }

.sw-close {
  position: absolute;
  top: 16px;
  right: 16px;
  width: 32px;
  height: 32px;
  border: 0;
  border-radius: 50%;
  background: transparent;
  font-size: 24px;
  line-height: 1;
  color: #6b7280;
  cursor: pointer;
}
.sw-close:hover { background: #f3f4f6; }

.sw-question { margin: 0 0 20px; padding: 0; border: 0; min-width: 0; }
.sw-question-text { padding: 0; margin-bottom: 12px; font-size: 16px; font-weight: 500; }
.sw-required { color: #dc2626; }

.sw-scale {
  display: grid;
  grid-template-columns: repeat(var(--sw-scale-count, 11), minmax(0, 1fr));
  gap: 6px;
}
.sw-scale-item {
  min-width: 0;
  height: 40px;
  padding: 0;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  background: #fff;
  font-size: 15px;
  color: inherit;
  cursor: pointer;
}
.sw-scale-item:hover { border-color: #2563eb; }
.sw-scale-item[aria-checked="true"] { border-color: #2563eb; background: #2563eb; color: #fff; }
.sw-scale-labels { display: flex; justify-content: space-between; margin-top: 6px; font-size: 13px; color: #6b7280; }

.sw-stars { display: flex; gap: 4px; }
.sw-star { border: 0; background: none; font-size: 32px; color: #d1d5db; cursor: pointer; padding: 0 2px; }
.sw-star-on { color: #f59e0b; }

.sw-textarea {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  font: inherit;
  font-size: 15px;
  resize: vertical;
}
.sw-textarea:focus, .sw-scale-item:focus-visible, .sw-submit:focus-visible, .sw-close:focus-visible {
  outline: 2px solid #2563eb;
  outline-offset: 1px;
}
.sw-counter { margin-top: 4px; text-align: right; font-size: 12px; color: #9ca3af; }

.sw-option { display: flex; align-items: center; gap: 8px; padding: 6px 0; font-size: 15px; cursor: pointer; }

.sw-error { margin: 0 0 12px; font-size: 14px; color: #dc2626; }

.sw-submit {
  width: 100%;
  height: 48px;
  border: 0;
  border-radius: 10px;
  background: #2563eb;
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
}
.sw-submit:disabled { background: #93c5fd; cursor: not-allowed; }

.sw-thanks { padding: 16px 0 8px; text-align: center; }
.sw-thanks-title { margin: 0 0 8px; font-size: 20px; font-weight: 600; }
.sw-thanks-text { margin: 0; color: #6b7280; }

/* Мобильная версия: нижняя шторка */
@media (max-width: 600px) {
  .sw-overlay { align-items: flex-end; padding: 0; }
  .sw-popup {
    max-width: none;
    max-height: 90vh;
    max-height: 90dvh;
    border-radius: 16px 16px 0 0;
    padding: 20px 16px calc(16px + env(safe-area-inset-bottom));
  }
  .sw-scale { gap: 4px; }
  .sw-scale-item { height: 36px; font-size: 14px; border-radius: 6px; }
  .sw-actions { position: sticky; bottom: 0; background: #fff; padding-top: 8px; }
}
`;
