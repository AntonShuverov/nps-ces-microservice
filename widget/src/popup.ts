import type { SurveyApi } from './api';
import { createQuestion, h, type QuestionComponent } from './questions';
import { styles } from './styles';
import type { ActiveSurvey, AnswerValue, Texts } from './types';

export interface PopupOptions {
  texts: Texts;
  closeOnEsc: boolean;
  closeOnOutsideClick: boolean;
  thankYouAutoCloseMs: number;
}

/**
 * Поп-ап опроса (docs/survey-service.md, п. 8.2): шаги, индикатор «1/2», крестик,
 * блокировка кнопки до ответа сервиса, одна автоматическая повторная попытка, экран благодарности.
 */
export class SurveyPopup {
  readonly host: HTMLElement;
  private readonly root: ShadowRoot;
  private readonly popup: HTMLElement;
  private impression: Promise<string> | null = null;
  private stepIndex = 0;
  private finished = false;
  private closed = false;
  private autoCloseTimer: ReturnType<typeof setTimeout> | undefined;
  private readonly onKeyDown = (event: KeyboardEvent) => {
    if (event.key === 'Escape') {
      this.close();
    }
  };

  constructor(
    private readonly survey: ActiveSurvey,
    private readonly api: SurveyApi,
    private readonly options: PopupOptions,
    private readonly onClosed: () => void,
  ) {
    this.host = h('div', { 'data-survey-widget': survey.code });
    this.root = this.host.attachShadow({ mode: 'open' });
    this.root.append(h('style', {}, styles));

    const overlay = h('div', { class: 'sw-overlay' });
    this.popup = h('div', {
      class: 'sw-popup',
      role: 'dialog',
      'aria-modal': 'true',
      'aria-labelledby': 'sw-title',
    });
    overlay.append(this.popup);
    this.root.append(overlay);

    if (options.closeOnOutsideClick) {
      overlay.addEventListener('click', (event) => {
        if (event.target === overlay) {
          this.close();
        }
      });
    }
  }

  /** Отрисовывает поп-ап. Показ фиксируется отдельно через {@link setImpression} после отрисовки. */
  mount(): void {
    document.body.append(this.host);
    if (this.options.closeOnEsc) {
      document.addEventListener('keydown', this.onKeyDown);
    }
    this.renderStep();
  }

  /** Показ фиксируется параллельно с отрисовкой. Отправка ответов и закрытие дожидаются его ID. */
  setImpression(impression: Promise<string>): void {
    this.impression = impression;
    impression.catch(() => undefined);
  }

  /** Убрать поп-ап без отправки закрытия (например, если сервис отказал в фиксации показа). */
  dismiss(): void {
    if (this.closed) {
      return;
    }
    this.closed = true;
    clearTimeout(this.autoCloseTimer);
    document.removeEventListener('keydown', this.onKeyDown);
    this.host.remove();
    this.onClosed();
  }

  /** Закрытие крестиком: сервис фиксирует шаг, на котором клиент закрыл опрос. */
  close(): void {
    if (this.closed) {
      return;
    }
    if (this.impression && !this.finished) {
      const step = this.survey.steps[this.stepIndex].step;
      this.impression.then((id) => this.api.close(id, step)).catch(() => undefined);
    }
    this.dismiss();
  }

  private renderHeader(): HTMLElement {
    const header = h('div', { class: 'sw-header' }, h('h2', { class: 'sw-title', id: 'sw-title' }, this.survey.title));
    if (!this.finished && this.survey.steps.length > 1) {
      header.append(h('span', { class: 'sw-progress' }, `${this.stepIndex + 1}/${this.survey.steps.length}`));
    }
    const close = h('button', { type: 'button', class: 'sw-close', 'aria-label': this.options.texts.close }, '×');
    close.addEventListener('click', () => this.close());
    return h('div', {}, header, close);
  }

  private renderStep(): void {
    const step = this.survey.steps[this.stepIndex];
    const submit = h('button', { type: 'submit', class: 'sw-submit', disabled: '' }, this.options.texts.submit);
    const error = h('p', { class: 'sw-error', role: 'alert', hidden: '' });

    const updateSubmit = () => {
      submit.disabled = !components.every((c) => !c.question.required || c.getValue() !== undefined);
    };
    const components: QuestionComponent[] = step.questions.map((q) =>
      createQuestion(q, this.options.texts, () => {
        error.hidden = true;
        updateSubmit();
      }),
    );
    updateSubmit();

    const form = h('form', { novalidate: '' }, ...components.map((c) => c.element), h('div', { class: 'sw-actions' }, error, submit));
    form.addEventListener('submit', (event) => {
      event.preventDefault();
      void this.submitStep(step.step, components, submit, error, updateSubmit);
    });

    this.popup.replaceChildren(this.renderHeader(), form);
  }

  private async submitStep(
    step: number,
    components: QuestionComponent[],
    submit: HTMLButtonElement,
    error: HTMLElement,
    updateSubmit: () => void,
  ): Promise<void> {
    if (!this.impression) {
      return;
    }
    const answers = new Map<number, AnswerValue>();
    for (const component of components) {
      const value = component.getValue();
      if (value !== undefined) {
        answers.set(component.question.id, value);
      }
    }

    submit.disabled = true;
    error.hidden = true;
    try {
      const impressionId = await this.impression;
      const result = await this.saveWithRetry(impressionId, step, answers);
      if (this.closed) {
        return;
      }
      if (result.completed || this.stepIndex + 1 >= this.survey.steps.length) {
        this.showThanks();
      } else {
        this.stepIndex++;
        this.renderStep();
      }
    } catch {
      if (this.closed) {
        return;
      }
      error.textContent = this.options.texts.sendError;
      error.hidden = false;
      updateSubmit();
    }
  }

  /** Одна автоматическая повторная попытка, затем сообщение об ошибке. */
  private async saveWithRetry(impressionId: string, step: number, answers: Map<number, AnswerValue>) {
    try {
      return await this.api.saveStep(impressionId, step, answers);
    } catch {
      return await this.api.saveStep(impressionId, step, answers);
    }
  }

  private showThanks(): void {
    this.finished = true;
    const thanks = h(
      'div',
      { class: 'sw-thanks' },
      h('p', { class: 'sw-thanks-title' }, this.options.texts.thankYouTitle),
      h('p', { class: 'sw-thanks-text' }, this.options.texts.thankYouText),
    );
    this.popup.replaceChildren(this.renderHeader(), thanks);
    if (this.options.thankYouAutoCloseMs > 0) {
      this.autoCloseTimer = setTimeout(() => this.dismiss(), this.options.thankYouAutoCloseMs);
    }
  }
}
