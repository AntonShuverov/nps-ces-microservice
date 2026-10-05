import type { AnswerValue, Question, ShowIf } from './types';

/** Компонент вопроса: свой для каждого типа (docs/survey-service.md, п. 8.1). */
export interface QuestionComponent {
  question: Question;
  element: HTMLElement;
  /** undefined — ответа нет. */
  getValue(): AnswerValue | undefined;
}

/** Смайлики CES по макету: от «очень сложно» до «очень легко». */
export const DEFAULT_EMOJI = ['😭', '😒', '😐', '☺️', '😍'];

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

export function createQuestion(question: Question, onChange: () => void): QuestionComponent {
  switch (question.type) {
    case 'SCALE':
      return question.settings.view === 'emoji' ? emojiScale(question, onChange) : scale(question, onChange);
    case 'STARS':
      return stars(question, onChange);
    case 'TEXT':
      return text(question, onChange);
    case 'SINGLE_CHOICE':
      return singleChoice(question, onChange);
    case 'MULTIPLE_CHOICE':
      return multipleChoice(question, onChange);
  }
}

/** Виден ли вопрос при текущих ответах (settings.showIf), см. VisibilityRule на бэкенде. */
export function isVisible(showIf: ShowIf | undefined, answers: Map<string, AnswerValue>): boolean {
  if (!showIf) {
    return true;
  }
  const actual = answers.get(showIf.question);
  if (typeof actual !== 'number') {
    return false;
  }
  const expected = showIf.value;
  switch (showIf.op) {
    case 'in':
      return Array.isArray(expected) && expected.includes(actual);
    case 'eq':
      return actual === expected;
    case 'neq':
      return actual !== expected;
    case 'gt':
      return actual > (expected as number);
    case 'gte':
      return actual >= (expected as number);
    case 'lt':
      return actual < (expected as number);
    case 'lte':
      return actual <= (expected as number);
    default:
      return false;
  }
}

function wrapper(question: Question): { root: HTMLElement; labelId: string } {
  const labelId = nextId('sw-q');
  const root = h('div', { class: 'sw-question', role: 'group', 'aria-labelledby': labelId, 'data-code': question.code });
  root.append(h('div', { class: 'sw-question-text', id: labelId }, question.text));
  return { root, labelId };
}

/** Радиокнопки с цифрами под ними (NPS по макету). */
function scale(question: Question, onChange: () => void): QuestionComponent {
  const min = question.settings.min ?? 0;
  const max = question.settings.max ?? 10;
  const { root, labelId } = wrapper(question);
  let value: number | undefined;

  const group = h('div', { class: 'sw-scale', role: 'radiogroup', 'aria-labelledby': labelId });
  const buttons: HTMLButtonElement[] = [];
  for (let n = min; n <= max; n++) {
    const button = h(
      'button',
      { type: 'button', role: 'radio', 'aria-checked': 'false', class: 'sw-scale-item' },
      h('span', { class: 'sw-radio', 'aria-hidden': 'true' }),
      h('span', { class: 'sw-scale-number' }, String(n)),
    );
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

/** Шкала смайликами (CES по макету): после выбора остальные смайлики бледнеют. */
function emojiScale(question: Question, onChange: () => void): QuestionComponent {
  const min = question.settings.min ?? 1;
  const max = question.settings.max ?? 5;
  const icons = question.settings.icons ?? DEFAULT_EMOJI;
  const { root, labelId } = wrapper(question);
  let value: number | undefined;

  const group = h('div', { class: 'sw-emoji-scale', role: 'radiogroup', 'aria-labelledby': labelId });
  const buttons: HTMLButtonElement[] = [];
  for (let n = min; n <= max; n++) {
    const button = h('button', {
      type: 'button',
      role: 'radio',
      'aria-checked': 'false',
      'aria-label': `${n} из ${max}`,
      class: 'sw-emoji',
    }, icons[n - min] ?? String(n));
    button.addEventListener('click', () => {
      value = n;
      group.classList.add('sw-has-value');
      buttons.forEach((b, i) => b.setAttribute('aria-checked', String(min + i === n)));
      onChange();
    });
    buttons.push(button);
    group.append(button);
  }
  root.append(group);
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

function text(question: Question, onChange: () => void): QuestionComponent {
  const maxLength = question.settings.maxLength ?? 1000;
  const { root, labelId } = wrapper(question);
  const textarea = h('textarea', {
    class: 'sw-textarea',
    rows: '3',
    maxlength: String(maxLength),
    'aria-labelledby': labelId,
    placeholder: question.settings.placeholder ?? '',
  });
  textarea.addEventListener('input', onChange);
  // На мобильной версии клавиатура не должна перекрывать кнопку «Отправить».
  textarea.addEventListener('focus', () => {
    setTimeout(() => root.closest('.sw-popup')?.querySelector('.sw-submit')?.scrollIntoView?.({ block: 'nearest' }), 300);
  });
  root.append(textarea);
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
