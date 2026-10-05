import { SurveyApi } from './api';
import { SurveyPopup } from './popup';
import {
  KNOWN_QUESTION_TYPES,
  type ActiveSurvey,
  type ShowSurveyOptions,
  type SurveyWidgetOptions,
  type Texts,
} from './types';

export type * from './types';

const DEFAULT_TEXTS: Texts = {
  submit: 'Отправить',
  close: 'Закрыть',
  closeButton: 'Закрыть',
  thankYou: 'Спасибо, что помогаете становиться лучше!',
  sendError: 'Не удалось отправить, попробуйте еще раз',
};

interface Config {
  api: SurveyApi;
  delayMs: number;
  requestTimeoutMs: number;
  isOtherModalOpen: () => boolean;
  closeOnEsc: boolean;
  closeOnOutsideClick: boolean;
  thankYouAutoCloseMs: number;
  texts: Texts;
}

let config: Config = buildConfig({});
let current: SurveyPopup | null = null;
const requestedThisVisit = new Set<string>();

/** Настройка виджета. Вызывается один раз при загрузке сайта. */
export function initSurveys(options: SurveyWidgetOptions = {}): void {
  config = buildConfig(options);
}

/**
 * Показать опрос на шаге флоу, если он есть (docs/survey-service.md, п. 8.3).
 * Вызывается на каждом шаге из списка триггеров. Любая ошибка глушится: флоу клиента не должен ломаться.
 */
export async function showSurvey(flowStep: string, options: ShowSurveyOptions = {}): Promise<void> {
  const visitKey = `${flowStep}|${options.eventObjectId ?? ''}`;
  if (requestedThisVisit.has(visitKey)) {
    return;
  }
  requestedThisVisit.add(visitKey);

  try {
    await sleep(config.delayMs);
    if (isPopupOpen() || config.isOtherModalOpen()) {
      return;
    }
    const survey = await config.api.getActive(flowStep, config.requestTimeoutMs);
    if (!survey || !isRenderable(survey) || isPopupOpen() || config.isOtherModalOpen()) {
      return;
    }

    const popup = new SurveyPopup(survey, config.api, config, () => {
      if (current === popup) {
        current = null;
      }
    });
    current = popup;
    popup.mount();

    const impression = config.api.createImpression(survey.surveyId, flowStep, options.eventObjectId);
    popup.setImpression(impression);
    // Если сервис отказал (например, опрос уже показан в другой вкладке), поп-ап закрывается.
    impression.catch(() => popup.dismiss());
  } catch {
    // Таймаут или ошибка сервиса: опрос не показывается, флоу продолжается.
  }
}

/** Сбросить защиту от повторного вызова, например при новом визите на шаг в SPA. */
export function resetSurveyVisit(flowStep?: string): void {
  if (flowStep === undefined) {
    requestedThisVisit.clear();
    return;
  }
  for (const key of [...requestedThisVisit]) {
    if (key.startsWith(`${flowStep}|`)) {
      requestedThisVisit.delete(key);
    }
  }
}

/** Поп-ап, который сайт удалил из DOM (например, при перерисовке), считается закрытым. */
function isPopupOpen(): boolean {
  return current !== null && current.host.isConnected;
}

/** Неизвестный тип вопроса — опрос не показывается целиком (п. 8.1). */
function isRenderable(survey: ActiveSurvey): boolean {
  return (
    survey.steps.length > 0 &&
    survey.steps.every((step) => step.questions.length > 0 && step.questions.every((q) => KNOWN_QUESTION_TYPES.includes(q.type)))
  );
}

function defaultIsOtherModalOpen(): boolean {
  return document.querySelector('[aria-modal="true"], dialog[open]') !== null;
}

function buildConfig(options: SurveyWidgetOptions): Config {
  return {
    api: new SurveyApi(options.baseUrl ?? '/api/v1/surveys', options.headers ?? (() => ({}))),
    delayMs: options.delayMs ?? 2000,
    requestTimeoutMs: options.requestTimeoutMs ?? 1500,
    isOtherModalOpen: options.isOtherModalOpen ?? defaultIsOtherModalOpen,
    closeOnEsc: options.closeOnEsc ?? false,
    closeOnOutsideClick: options.closeOnOutsideClick ?? false,
    thankYouAutoCloseMs: options.thankYouAutoCloseMs ?? 4000,
    texts: { ...DEFAULT_TEXTS, ...options.texts },
  };
}

function sleep(ms: number): Promise<void> {
  return ms > 0 ? new Promise((resolve) => setTimeout(resolve, ms)) : Promise.resolve();
}
