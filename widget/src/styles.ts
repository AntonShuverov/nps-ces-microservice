/**
 * Стили поп-апа по макетам Figma «NPS CES». Живут внутри Shadow DOM и не пересекаются со стилями сайта.
 * Шрифты как в макете: тексты — Mazzard M, цифры шкалы — Montserrat. Виджет не загружает шрифты сам,
 * а использует подключенные на сайте. Их можно переопределить CSS-переменными --sw-font и --sw-digits-font.
 */
export const styles = `
:host { all: initial; }
* { box-sizing: border-box; }

.sw-overlay {
  --sw-text: #3c546b;
  --sw-title: #111e2b;
  --sw-muted: #8ea3b6;
  --sw-icon: #657f95;
  --sw-accent: #166cb7;
  --sw-button: #ff6016;
  --sw-field: #ecf3f8;

  position: fixed;
  inset: 0;
  z-index: 2147483000;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 16px;
  background: rgba(17, 30, 43, 0.4);
  font-family: var(--sw-font, "Mazzard M", "Mazzard", -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Arial, sans-serif);
  color: var(--sw-text);
  -webkit-font-smoothing: antialiased;
}

.sw-handle { display: none; }

.sw-popup {
  position: relative;
  width: 100%;
  max-width: 320px;
  max-height: calc(100vh - 32px);
  overflow-y: auto;
  border-radius: 20px;
  background: #fff;
  box-shadow: 0 20px 50px rgba(17, 30, 43, 0.2);
}

.sw-header {
  display: grid;
  grid-template-columns: 30px 1fr 30px;
  align-items: center;
  gap: 10px;
  padding: 20px;
}
.sw-progress { font-size: 11px; line-height: 12px; color: var(--sw-muted); }
.sw-title {
  margin: 0;
  text-align: center;
  font-size: 16px;
  line-height: 20px;
  font-weight: 500;
  color: var(--sw-title);
}
.sw-close {
  justify-self: end;
  display: flex;
  width: 20px;
  height: 20px;
  padding: 0;
  border: 0;
  background: none;
  color: var(--sw-icon);
  cursor: pointer;
}

form { margin: 0; }

.sw-question { padding: 20px 30px; min-width: 0; }
.sw-question[hidden] { display: none; }
.sw-question-text {
  margin-bottom: 20px;
  text-align: center;
  font-size: 16px;
  line-height: 20px;
  color: var(--sw-text);
}

/* NPS: радиокнопки с цифрами под ними */
.sw-scale { display: flex; justify-content: space-between; gap: 4px; }
.sw-scale-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  min-width: 0;
  padding: 0;
  border: 0;
  background: none;
  font: inherit;
  color: var(--sw-text);
  cursor: pointer;
}
.sw-radio {
  width: 12px;
  height: 12px;
  border: 2px solid var(--sw-text);
  border-radius: 50%;
}
.sw-scale-item[aria-checked="true"] .sw-radio {
  border-color: var(--sw-accent);
  background: radial-gradient(circle, var(--sw-accent) 0 2px, transparent 2.5px);
}
.sw-scale-number { font-family: var(--sw-digits-font, "Montserrat", inherit); font-size: 12px; line-height: 15px; }
.sw-scale-labels { display: flex; justify-content: space-between; margin-top: 8px; font-size: 12px; color: var(--sw-muted); }

/* CES: смайлики, после выбора остальные бледнеют */
.sw-emoji-scale { display: flex; justify-content: center; gap: 8px; }
.sw-emoji {
  width: 40px;
  height: 40px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: none;
  font-size: 34px;
  line-height: 40px;
  cursor: pointer;
  transition: opacity 0.15s;
}
.sw-has-value .sw-emoji { opacity: 0.25; }
.sw-has-value .sw-emoji[aria-checked="true"] { opacity: 1; }

.sw-stars { display: flex; justify-content: center; gap: 4px; }
.sw-star { border: 0; background: none; font-size: 32px; color: #d1d5db; cursor: pointer; padding: 0 2px; }
.sw-star-on { color: #f59e0b; }

.sw-textarea {
  display: block;
  width: 100%;
  min-height: 100px;
  padding: 20px;
  border: 0;
  border-radius: 10px;
  background: var(--sw-field);
  font: inherit;
  font-size: 14px;
  line-height: 20px;
  color: var(--sw-title);
  resize: none;
}
.sw-textarea::placeholder { color: var(--sw-icon); }

.sw-scale-item:focus-visible .sw-radio, .sw-emoji:focus-visible, .sw-textarea:focus-visible,
.sw-submit:focus-visible, .sw-close:focus-visible {
  outline: 2px solid var(--sw-accent);
  outline-offset: 2px;
}

.sw-option { display: flex; align-items: center; gap: 8px; padding: 6px 0; font-size: 15px; cursor: pointer; }

.sw-actions { padding: 20px 30px 30px; }
.sw-actions[hidden] { display: none; }
.sw-error { margin: 0 0 12px; text-align: center; font-size: 14px; color: #dc2626; }
.sw-submit {
  width: 100%;
  height: 52px;
  padding: 0 24px;
  border: 0;
  border-radius: 24px;
  background: var(--sw-button);
  color: #fff;
  font: inherit;
  font-size: 16px;
  font-weight: 500;
  line-height: 16px;
  text-transform: uppercase;
  cursor: pointer;
}
.sw-submit:disabled { opacity: 0.6; cursor: wait; }

/* Экран благодарности: текст и кнопка «Закрыть», без заголовка */
.sw-finished .sw-header { padding-bottom: 0; }
.sw-thanks-text {
  margin: 0;
  padding: 0 30px;
  text-align: center;
  font-size: 16px;
  line-height: 20px;
  color: var(--sw-title);
}
.sw-thanks .sw-actions { padding-top: 20px; }

/* Мобильная версия: нижняя шторка с «ручкой» */
@media (max-width: 600px) {
  .sw-overlay { justify-content: flex-end; padding: 0; }
  .sw-handle {
    display: block;
    width: 40px;
    height: 2px;
    margin-bottom: 5px;
    border-radius: 2px;
    background: #fff;
  }
  .sw-popup {
    max-width: none;
    max-height: 90vh;
    max-height: 90dvh;
    border-radius: 20px 20px 0 0;
    padding-bottom: env(safe-area-inset-bottom);
  }
  .sw-header { padding: 10px 20px; }
  .sw-question { padding: 20px; }
  .sw-actions { padding: 20px; position: sticky; bottom: 0; background: #fff; }
}
`;
