import type { AnswerValue, Question, Texts } from './types';

/** Компонент вопроса: свой для каждого типа (docs/survey-service.md, п. 8.1). */
export interface QuestionComponent {
  question: Question;
  element: HTMLElement;
  /** undefined — ответа нет. */
  getValue(): AnswerValue | undefined;
}

export function h<K extends keyof HTMLElementTagNameMap>(
  tag: K,
  attrs: Record<string, string> = {},
  ...children: (Node | string)[]
): HTMLElementTagNameMap[K] {
  const element = document.createElement(tag);
  for (const [name, value] of Object.entries(attrs)) {
    element.setAttribute(name, value);
  }
  element.append(...children);
  return element;
}

let idCounter = 0;
const nextId = (prefix: string) => `${prefix}-${++idCounter}`;

export function createQuestion(question: Question, texts: Texts, onChange: () => void): QuestionComponent {
  switch (question.type) {
    case 'SCALE':
      return scale(question, onChange);
    case 'STARS':
      return stars(question, onChange);
    case 'TEXT':
      return text(question, texts, onChange);
    case 'SINGLE_CHOICE':
      return singleChoice(question, onChange);
    case 'MULTIPLE_CHOICE':
      return multipleChoice(question, onChange);
  }
}

function wrapper(question: Question): { root: HTMLElement; labelId: string } {
  const labelId = nextId('sw-q');
  const root = h('fieldset', { class: 'sw-question', 'data-code': question.code });
  const legend = h('legend', { class: 'sw-question-text', id: labelId }, question.text);
  if (question.required) {
    legend.append(h('span', { class: 'sw-required', 'aria-hidden': 'true' }, ' *'));
  }
  root.append(legend);
  return { root, labelId };
}

/** Ряд кнопок с цифрами. Работает и для NPS 0–10, и для CES 1–7. */
function scale(question: Question, onChange: () => void): QuestionComponent {
  const min = question.settings.min ?? 0;
  const max = question.settings.max ?? 10;
  const { root, labelId } = wrapper(question);
  let value: number | undefined;

  const group = h('div', { class: 'sw-scale', role: 'radiogroup', 'aria-labelledby': labelId });
  group.style.setProperty('--sw-scale-count', String(max - min + 1));
  const buttons: HTMLButtonElement[] = [];
  for (let n = min; n <= max; n++) {
    const button = h('button', { type: 'button', role: 'radio', 'aria-checked': 'false', class: 'sw-scale-item' }, String(n));
    button.addEventListener('click', () => {
      value = n;
      buttons.forEach((b, i) => b.setAttribute('aria-checked', String(min + i === n)));
      onChange();
    });
    buttons.push(button);
    group.append(button);
  }
  root.append(group);

  if (question.settings.minLabel || question.settings.maxLabel) {
    root.append(
      h(
        'div',
        { class: 'sw-scale-labels' },
        h('span', {}, question.settings.minLabel ?? ''),
        h('span', {}, question.settings.maxLabel ?? ''),
      ),
    );
  }
  return { question, element: root, getValue: () => value };
}

function stars(question: Question, onChange: () => void): QuestionComponent {
  const count = question.settings.count ?? 5;
  const { root, labelId } = wrapper(question);
  let value: number | undefined;

  const group = h('div', { class: 'sw-stars', role: 'radiogroup', 'aria-labelledby': labelId });
  const buttons: HTMLButtonElement[] = [];
  for (let n = 1; n <= count; n++) {
    const button = h('button', {
      type: 'button',
      role: 'radio',
      'aria-checked': 'false',
      'aria-label': String(n),
      class: 'sw-star',
    }, '★');
    button.addEventListener('click', () => {
      value = n;
      buttons.forEach((b, i) => {
        b.setAttribute('aria-checked', String(i + 1 === n));
        b.classList.toggle('sw-star-on', i < n);
      });
      onChange();
    });
    buttons.push(button);
    group.append(button);
  }
  root.append(group);
  return { question, element: root, getValue: () => value };
}

function text(question: Question, texts: Texts, onChange: () => void): QuestionComponent {
  const maxLength = question.settings.maxLength ?? 1000;
  const { root, labelId } = wrapper(question);
  const textarea = h('textarea', {
    class: 'sw-textarea',
    rows: '3',
    maxlength: String(maxLength),
    'aria-labelledby': labelId,
    placeholder: question.settings.placeholder ?? '',
  });
  const counter = h('div', { class: 'sw-counter', 'aria-live': 'polite' }, texts.charactersLeft(maxLength));
  textarea.addEventListener('input', () => {
    counter.textContent = texts.charactersLeft(maxLength - textarea.value.length);
    onChange();
  });
  // На мобильной версии клавиатура не должна перекрывать кнопку «Отправить».
  textarea.addEventListener('focus', () => {
    setTimeout(() => root.closest('.sw-popup')?.querySelector('.sw-submit')?.scrollIntoView?.({ block: 'nearest' }), 300);
  });
  root.append(textarea, counter);
  return {
    question,
    element: root,
    getValue: () => (textarea.value.trim() === '' ? undefined : textarea.value),
  };
}

function singleChoice(question: Question, onChange: () => void): QuestionComponent {
  const { root } = wrapper(question);
  const name = nextId('sw-single');
  let value: string | undefined;
  for (const option of question.settings.options ?? []) {
    const input = h('input', { type: 'radio', name, value: option.code });
    input.addEventListener('change', () => {
      value = option.code;
      onChange();
    });
    root.append(h('label', { class: 'sw-option' }, input, h('span', {}, option.label)));
  }
  return { question, element: root, getValue: () => value };
}

function multipleChoice(question: Question, onChange: () => void): QuestionComponent {
  const { root } = wrapper(question);
  const maxSelected = question.settings.maxSelected ?? Infinity;
  const inputs: HTMLInputElement[] = [];
  for (const option of question.settings.options ?? []) {
    const input = h('input', { type: 'checkbox', value: option.code });
    input.addEventListener('change', () => {
      const selectedCount = inputs.filter((i) => i.checked).length;
      inputs.forEach((i) => {
        i.disabled = !i.checked && selectedCount >= maxSelected;
      });
      onChange();
    });
    inputs.push(input);
    root.append(h('label', { class: 'sw-option' }, input, h('span', {}, option.label)));
  }
  return {
    question,
    element: root,
    getValue: () => {
      const selected = inputs.filter((i) => i.checked).map((i) => i.value);
      return selected.length === 0 ? undefined : selected;
    },
  };
}
