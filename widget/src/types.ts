/** Типы ответов сервиса опросов (docs/survey-service.md, п. 7). */

export type QuestionType = 'SCALE' | 'TEXT' | 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE' | 'STARS';

export const KNOWN_QUESTION_TYPES: readonly string[] = [
  'SCALE',
  'TEXT',
  'SINGLE_CHOICE',
  'MULTIPLE_CHOICE',
  'STARS',
];

export interface ChoiceOption {
  code: string;
  label: string;
}

export interface QuestionSettings {
  min?: number;
  max?: number;
  minLabel?: string;
  maxLabel?: string;
  placeholder?: string;
  maxLength?: number;
  options?: ChoiceOption[];
  maxSelected?: number;
  count?: number;
}

export interface Question {
  id: number;
  code: string;
  type: QuestionType;
  text: string;
  required: boolean;
  settings: QuestionSettings;
}

export interface SurveyStep {
  step: number;
  questions: Question[];
}

export interface ActiveSurvey {
  surveyId: number;
  code: string;
  title: string;
  steps: SurveyStep[];
}

/** Значение ответа: число для шкалы и звезд, строка для текста и выбора одного, массив для выбора нескольких. */
export type AnswerValue = number | string | string[];

export interface StepResult {
  status: 'SHOWN' | 'CLOSED' | 'COMPLETED';
  lastStep: number;
  completed: boolean;
}

export interface Texts {
  submit: string;
  close: string;
  thankYouTitle: string;
  thankYouText: string;
  sendError: string;
  charactersLeft: (left: number) => string;
}

export interface SurveyWidgetOptions {
  /** Базовый URL API. По умолчанию тот же домен: /api/v1/surveys. */
  baseUrl?: string;
  /** Задержка перед запросом опроса, чтобы клиент сначала увидел результат шага. */
  delayMs?: number;
  /** Таймаут запроса активного опроса. При таймауте опрос не показывается. */
  requestTimeoutMs?: number;
  /** Дополнительные заголовки (например, токен), если авторизация не на cookie. */
  headers?: () => Record<string, string>;
  /** Открыто ли на странице другое модальное окно. По умолчанию ищет [aria-modal="true"] и dialog[open]. */
  isOtherModalOpen?: () => boolean;
  /** Закрывать по Esc. Фиксируется как закрытие крестиком. */
  closeOnEsc?: boolean;
  /** Закрывать по клику вне поп-апа. Фиксируется как закрытие крестиком. */
  closeOnOutsideClick?: boolean;
  /** Через сколько миллисекунд закрывать экран благодарности, 0 — не закрывать автоматически. */
  thankYouAutoCloseMs?: number;
  /** Тексты интерфейса. */
  texts?: Partial<Texts>;
}

export interface ShowSurveyOptions {
  /** ID объекта события, например ID займа. Сохраняется в показе для связи с DWH. */
  eventObjectId?: string;
}
