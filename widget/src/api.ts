import type { ActiveSurvey, AnswerValue, StepResult } from './types';

export class SurveyApiError extends Error {
  constructor(readonly status: number) {
    super(`Survey API error: ${status}`);
  }
}

/** HTTP-клиент сервиса опросов. Клиент определяется сервисом по сессии, ID клиента не передается. */
export class SurveyApi {
  constructor(
    private readonly baseUrl: string,
    private readonly headers: () => Record<string, string>,
  ) {}

  /** 7.1. Возвращает null, если опроса нет. */
  async getActive(flowStep: string, timeoutMs: number): Promise<ActiveSurvey | null> {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);
    try {
      const response = await this.request(`/active?flowStep=${encodeURIComponent(flowStep)}`, {
        method: 'GET',
        signal: controller.signal,
      });
      if (response.status === 204) {
        return null;
      }
      return (await response.json()) as ActiveSurvey;
    } finally {
      clearTimeout(timer);
    }
  }

  /** 7.2. */
  async createImpression(surveyId: number, flowStep: string, eventObjectId?: string): Promise<string> {
    const response = await this.request('/impressions', {
      method: 'POST',
      body: JSON.stringify({ surveyId, flowStep, eventObjectId }),
    });
    const body = (await response.json()) as { impressionId: string };
    return body.impressionId;
  }

  /** 7.3. */
  async saveStep(impressionId: string, step: number, answers: Map<number, AnswerValue>): Promise<StepResult> {
    const response = await this.request(`/impressions/${encodeURIComponent(impressionId)}/steps/${step}`, {
      method: 'PUT',
      body: JSON.stringify({
        answers: [...answers].map(([questionId, value]) => ({ questionId, value })),
      }),
    });
    return (await response.json()) as StepResult;
  }

  /** 7.4. keepalive позволяет отправить закрытие, даже если клиент уходит со страницы. */
  async close(impressionId: string, step: number): Promise<void> {
    await this.request(`/impressions/${encodeURIComponent(impressionId)}/close`, {
      method: 'POST',
      body: JSON.stringify({ step }),
      keepalive: true,
    });
  }

  private async request(path: string, init: RequestInit): Promise<Response> {
    const response = await fetch(`${this.baseUrl}${path}`, {
      ...init,
      credentials: 'include',
      headers: { 'Content-Type': 'application/json', ...this.headers() },
    });
    if (!response.ok) {
      throw new SurveyApiError(response.status);
    }
    return response;
  }
}
