import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { initSurveys, resetSurveyVisit, showSurvey } from '../src/index';
import type { ActiveSurvey } from '../src/types';

// Как в макете: шаг 1 — NPS, шаг 2 — CES смайликами, комментарий только при низкой оценке
const SURVEY: ActiveSurvey = {
  surveyId: 2,
  code: 'nps_ces_loan_repaid',
  title: 'Пройдите опрос',
  steps: [
    {
      step: 1,
      questions: [
        { id: 10, code: 'nps', type: 'SCALE', text: 'Порекомендуете?', required: true, settings: { min: 1, max: 10 } },
        {
          id: 11, code: 'nps_comment', type: 'TEXT', text: 'Посоветуйте, что можно сделать лучше', required: false,
          settings: { maxLength: 100, showIf: { question: 'nps', op: 'lte', value: 6 } },
        },
      ],
    },
    {
      step: 2,
      questions: [
        { id: 12, code: 'ces', type: 'SCALE', text: 'Насколько легко?', required: true, settings: { min: 1, max: 5, view: 'emoji' } },
        {
          id: 13, code: 'ces_comment', type: 'TEXT', text: 'С какими трудностями вы столкнулись?', required: false,
          settings: { maxLength: 100, showIf: { question: 'ces', op: 'lte', value: 3 } },
        },
      ],
    },
  ],
};

interface Call {
  method: string;
  url: string;
  body: unknown;
}

let calls: Call[];
let responder: (call: Call) => Response | Promise<Response>;

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

function defaultResponder(call: Call): Response {
  if (call.url.includes('/active')) return json(SURVEY);
  if (call.url.endsWith('/impressions')) return json({ impressionId: 'imp-1' }, 201);
  if (call.url.includes('/steps/1')) return json({ status: 'SHOWN', lastStep: 1, completed: false });
  if (call.url.includes('/steps/2')) return json({ status: 'COMPLETED', lastStep: 2, completed: true });
  if (call.url.endsWith('/close')) return new Response(null, { status: 204 });
  return new Response(null, { status: 404 });
}

const root = () => document.querySelector('[data-survey-widget]')?.shadowRoot ?? null;
const flush = () => new Promise((resolve) => setTimeout(resolve, 0));

async function waitFor<T>(check: () => T | null | undefined | false): Promise<T> {
  for (let i = 0; i < 50; i++) {
    const result = check();
    if (result) return result;
    await flush();
  }
  throw new Error('Condition not met');
}

function scaleButton(value: string): HTMLButtonElement {
  return [...root()!.querySelectorAll<HTMLButtonElement>('.sw-scale-item')].find((b) => b.textContent === value)!;
}

function emojiButton(value: number): HTMLButtonElement {
  return root()!.querySelectorAll<HTMLButtonElement>('.sw-emoji')[value - 1];
}

function question(code: string): HTMLElement {
  return root()!.querySelector<HTMLElement>(`[data-code="${code}"]`)!;
}

function actionsHidden(): boolean {
  return root()!.querySelector<HTMLElement>('.sw-actions')!.hidden === true;
}

function submitButton(): HTMLButtonElement {
  return root()!.querySelector<HTMLButtonElement>('.sw-submit')!;
}

beforeEach(() => {
  calls = [];
  responder = defaultResponder;
  vi.stubGlobal('fetch', async (url: string, init: RequestInit) => {
    const call = { method: init.method ?? 'GET', url, body: init.body ? JSON.parse(String(init.body)) : undefined };
    calls.push(call);
    return responder(call);
  });
  initSurveys({ delayMs: 0, thankYouAutoCloseMs: 0 });
  resetSurveyVisit();
});

afterEach(() => {
  document.body.innerHTML = '';
  vi.unstubAllGlobals();
});

describe('showSurvey', () => {
  it('walks through steps as in design, sends typed answers and shows thank-you screen', async () => {
    await showSurvey('loan_issued', { eventObjectId: 'loan-42' });

    expect(root()!.querySelector('.sw-title')!.textContent).toBe('Пройдите опрос');
    expect(root()!.querySelector('.sw-progress')!.textContent).toBe('1/2');
    expect(root()!.querySelectorAll('.sw-scale-item')).toHaveLength(10);
    expect(calls[1]).toMatchObject({
      method: 'POST',
      url: '/api/v1/surveys/impressions',
      body: { surveyId: 2, flowStep: 'loan_issued', eventObjectId: 'loan-42' },
    });

    // До выбора оценки кнопки нет, комментарий скрыт
    expect(actionsHidden()).toBe(true);
    expect(question('nps_comment').hidden).toBe(true);

    // Высокая оценка: кнопка появилась, комментария нет
    scaleButton('9').click();
    expect(actionsHidden()).toBe(false);
    expect(question('nps_comment').hidden).toBe(true);
    submitButton().click();

    await waitFor(() => root()!.querySelector('.sw-progress')?.textContent === '2/2');
    expect(calls[2]).toMatchObject({
      method: 'PUT',
      url: '/api/v1/surveys/impressions/imp-1/steps/1',
      body: { answers: [{ questionId: 10, value: 9 }] },
    });
    expect(root()!.querySelectorAll('.sw-emoji')).toHaveLength(5);
    expect(root()!.querySelectorAll('.sw-emoji')[0].textContent).toBe('😭');

    // Низкий CES: появляется поле «С какими трудностями…»
    emojiButton(2).click();
    expect(question('ces_comment').hidden).toBe(false);
    const textarea = question('ces_comment').querySelector('textarea')!;
    textarea.value = 'Не прошел платеж';
    textarea.dispatchEvent(new Event('input'));
    submitButton().click();

    await waitFor(() => root()!.querySelector('.sw-thanks'));
    expect(calls[3].body).toEqual({ answers: [{ questionId: 12, value: 2 }, { questionId: 13, value: 'Не прошел платеж' }] });
    expect(root()!.querySelector('.sw-thanks-text')!.textContent).toBe('Спасибо, что помогаете становиться лучше!');

    root()!.querySelector<HTMLButtonElement>('.sw-thanks .sw-submit')!.click();
    await flush();
    expect(root()).toBeNull();
    expect(calls.some((c) => c.url.endsWith('/close'))).toBe(false);
  });

  it('shows comment for low NPS and hides it again for high one', async () => {
    await showSurvey('loan_issued');
    scaleButton('4').click();
    expect(question('nps_comment').hidden).toBe(false);
    const textarea = question('nps_comment').querySelector('textarea')!;
    textarea.value = 'Долго';
    textarea.dispatchEvent(new Event('input'));

    scaleButton('8').click();
    expect(question('nps_comment').hidden).toBe(true);
    submitButton().click();

    await waitFor(() => calls.find((c) => c.url.includes('/steps/1')));
    expect(calls.find((c) => c.url.includes('/steps/1'))!.body).toEqual({ answers: [{ questionId: 10, value: 8 }] });
  });

  it('reports the step where the popup was closed', async () => {
    await showSurvey('loan_issued');
    scaleButton('7').click();
    submitButton().click();
    await waitFor(() => root()!.querySelector('.sw-progress')?.textContent === '2/2');

    root()!.querySelector<HTMLButtonElement>('.sw-close')!.click();
    await waitFor(() => calls.find((c) => c.url.endsWith('/close')));

    expect(root()).toBeNull();
    expect(calls[calls.length - 1]).toMatchObject({ url: '/api/v1/surveys/impressions/imp-1/close', body: { step: 2 } });
  });

  it('does nothing when there is no survey', async () => {
    responder = () => new Response(null, { status: 204 });
    await showSurvey('loan_issued');
    expect(root()).toBeNull();
    expect(calls).toHaveLength(1);
  });

  it('does not show survey on service error', async () => {
    responder = () => new Response(null, { status: 500 });
    await expect(showSurvey('loan_issued')).resolves.toBeUndefined();
    expect(root()).toBeNull();
  });

  it('does not show survey with unknown question type', async () => {
    responder = (call) =>
      call.url.includes('/active')
        ? json({ ...SURVEY, steps: [{ step: 1, questions: [{ ...SURVEY.steps[0].questions[0], type: 'SLIDER' }] }] })
        : defaultResponder(call);
    await showSurvey('loan_issued');
    expect(root()).toBeNull();
    expect(calls.some((c) => c.url.endsWith('/impressions'))).toBe(false);
  });

  it('requests survey only once per visit to the step', async () => {
    await showSurvey('loan_issued');
    await showSurvey('loan_issued');
    expect(calls.filter((c) => c.url.includes('/active'))).toHaveLength(1);
  });

  it('skips survey when another modal is open', async () => {
    document.body.append(Object.assign(document.createElement('div'), { role: 'dialog' }));
    document.body.lastElementChild!.setAttribute('aria-modal', 'true');
    await showSurvey('loan_issued');
    expect(calls).toHaveLength(0);
  });

  it('closes popup when service rejects impression', async () => {
    responder = (call) => (call.url.endsWith('/impressions') ? new Response(null, { status: 409 }) : defaultResponder(call));
    await showSurvey('loan_issued');
    await waitFor(() => root() === null);
  });

  it('retries once and then shows error with possibility to resend', async () => {
    let failures = 2;
    responder = (call) => {
      if (call.url.includes('/steps/') && failures > 0) {
        failures--;
        return new Response(null, { status: 503 });
      }
      return defaultResponder(call);
    };
    await showSurvey('loan_issued');
    scaleButton('4').click();
    submitButton().click();

    const error = await waitFor(() => {
      const element = root()!.querySelector<HTMLElement>('.sw-error');
      return element && !element.hidden ? element : null;
    });
    expect(error.textContent).toBe('Не удалось отправить, попробуйте еще раз');
    expect(calls.filter((c) => c.url.includes('/steps/1'))).toHaveLength(2);
    expect(submitButton().disabled).toBe(false);

    submitButton().click();
    await waitFor(() => root()!.querySelector('.sw-progress')?.textContent === '2/2');
  });

  it('closes on click outside the popup by default', async () => {
    await showSurvey('loan_issued');
    root()!.querySelector<HTMLElement>('.sw-overlay')!.click();
    await waitFor(() => calls.find((c) => c.url.endsWith('/close')));
    expect(root()).toBeNull();
  });

  it('keeps popup open on outside click when disabled', async () => {
    initSurveys({ delayMs: 0, closeOnOutsideClick: false });
    await showSurvey('loan_issued');
    root()!.querySelector<HTMLElement>('.sw-overlay')!.click();
    await flush();
    expect(root()).not.toBeNull();
  });

  it('closes on Esc when enabled', async () => {
    initSurveys({ delayMs: 0, closeOnEsc: true });
    await showSurvey('loan_issued');
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    await waitFor(() => calls.find((c) => c.url.endsWith('/close')));
    expect(root()).toBeNull();
  });
});
