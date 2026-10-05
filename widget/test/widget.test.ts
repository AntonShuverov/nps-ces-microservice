import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { initSurveys, resetSurveyVisit, showSurvey } from '../src/index';
import type { ActiveSurvey } from '../src/types';

const SURVEY: ActiveSurvey = {
  surveyId: 2,
  code: 'ces_nps_after_loan_issued',
  title: 'Пройдите опрос',
  steps: [
    {
      step: 1,
      questions: [
        { id: 10, code: 'ces', type: 'SCALE', text: 'Насколько легко?', required: true, settings: { min: 1, max: 7 } },
      ],
    },
    {
      step: 2,
      questions: [
        { id: 11, code: 'nps', type: 'SCALE', text: 'Порекомендуете?', required: true, settings: { min: 0, max: 10 } },
        { id: 12, code: 'nps_comment', type: 'TEXT', text: 'Комментарий', required: false, settings: { maxLength: 100 } },
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
  it('walks through steps, sends typed answers and shows thank-you screen', async () => {
    await showSurvey('loan_issued', { eventObjectId: 'loan-42' });

    expect(root()!.querySelector('.sw-title')!.textContent).toBe('Пройдите опрос');
    expect(root()!.querySelector('.sw-progress')!.textContent).toBe('1/2');
    expect(root()!.querySelectorAll('.sw-scale-item')).toHaveLength(7);
    expect(calls[1]).toMatchObject({
      method: 'POST',
      url: '/api/v1/surveys/impressions',
      body: { surveyId: 2, flowStep: 'loan_issued', eventObjectId: 'loan-42' },
    });

    expect(submitButton().disabled).toBe(true);
    scaleButton('6').click();
    expect(submitButton().disabled).toBe(false);
    submitButton().click();

    await waitFor(() => root()!.querySelector('.sw-progress')?.textContent === '2/2');
    expect(calls[2]).toMatchObject({
      method: 'PUT',
      url: '/api/v1/surveys/impressions/imp-1/steps/1',
      body: { answers: [{ questionId: 10, value: 6 }] },
    });
    expect(root()!.querySelectorAll('.sw-scale-item')).toHaveLength(11);

    scaleButton('9').click();
    const textarea = root()!.querySelector('textarea')!;
    textarea.value = 'Отлично';
    textarea.dispatchEvent(new Event('input'));
    submitButton().click();

    await waitFor(() => root()!.querySelector('.sw-thanks'));
    expect(calls[3].body).toEqual({ answers: [{ questionId: 11, value: 9 }, { questionId: 12, value: 'Отлично' }] });

    root()!.querySelector<HTMLButtonElement>('.sw-close')!.click();
    await flush();
    expect(root()).toBeNull();
    expect(calls.some((c) => c.url.endsWith('/close'))).toBe(false);
  });

  it('reports the step where the popup was closed', async () => {
    await showSurvey('loan_issued');
    scaleButton('5').click();
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

  it('closes on Esc when enabled', async () => {
    initSurveys({ delayMs: 0, closeOnEsc: true });
    await showSurvey('loan_issued');
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    await waitFor(() => calls.find((c) => c.url.endsWith('/close')));
    expect(root()).toBeNull();
  });
});
