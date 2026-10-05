import type { SurveyApi } from './api';
import { createQuestion, h, isVisible, type QuestionComponent } from './questions';
import { styles } from './styles';
import type { ActiveSurvey, AnswerValue, Texts } from './types';

export interface PopupOptions {
  texts: Texts;
  closeOnEsc: boolean;
  closeOnOutsideClick: boolean;
  thankYouAutoCloseMs: number;
}

/**
 * Поп-ап опроса по макетам Figma «NPS CES» (docs/survey-service.md, п. 8.2): шаги, индикатор «1/2», крестик,
 * кнопка «Отправить» появляется после выбора оценки, комментарий — только при низкой оценке (settings.showIf),
 * блокировка кнопки до ответа сервиса, одна автоматическая повторная попытка, экран благодарности.
 */
export class SurveyPopup {
  readonly host: HTMLElement;
  private readonly root: ShadowRoot;
  private readonly popup: HTMLElement;
  private impression: Promise<string> | null = null;
  private stepIndex = 0;
  /** Ответы на пройденные шаги по коду вопроса: нужны для условий showIf на следующих шагах. */
  private readonly answeredByCode = new Map<string, AnswerValue>();
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
    // «Ручка» нижней шторки, видна только на мобильной версии
    overlay.append(h('div', { class: 'sw-handle', 'aria-hidden': 'true' }), this.popup);
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
    const header = h('div', { class: 'sw-header' });
    const progress = h('span', { class: 'sw-progress' });
    if (!this.finished && this.survey.steps.length > 1) {
      progress.textContent = `${this.stepIndex + 1}/${this.survey.steps.length}`;
    }
    const title = h('h2', { class: 'sw-title', id: 'sw-title' }, this.finished ? '' : this.survey.title);
    const close = h('button', { type: 'button', class: 'sw-close', 'aria-label': this.options.texts.close });
    close.innerHTML = '<svg width="20" height="20" viewBox="0 0 20 20" aria-hidden="true">'
      + '<path d="M3 3l14 14M17 3L3 17" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>';
    close.addEventListener('click', () => this.close());
    header.append(progress, title, close);
    return header;
  }

  private renderStep(): void {
    const step = this.survey.steps[this.stepIndex];
    const submit = h('button', { type: 'submit', class: 'sw-submit' }, this.options.texts.submit);
    const error = h('p', { class: 'sw-error', role: 'alert', hidden: '' });
    const actions = h('div', { class: 'sw-actions' }, error, submit);

    const visible = (c: QuestionComponent) => !c.element.hidden;
    const update = () => {
      const answers = new Map(this.answeredByCode);
      for (const c of components) {
        c.element.hidden = !isVisible(c.question.settings.showIf, answers);
        const value = c.getValue();
        if (visible(c) && value !== undefined) {
          answers.set(c.question.code, value);
        }
      }
      // По макету кнопка появляется, только когда заполнены обязательные вопросы шага
      const ready = components.every((c) => !visible(c) || !c.question.required || c.getValue() !== undefined);
      actions.hidden = !ready;
      submit.disabled = !ready;
    };
    const components: QuestionComponent[] = step.questions.map((q) =>
      createQuestion(q, () => {
        error.hidden = true;
        update();
      }),
    );
    update();

    const form = h('form', { novalidate: '' }, ...components.map((c) => c.element), actions);
    form.addEventListener('submit', (event) => {
      event.preventDefault();
      void this.submitStep(step.step, components.filter(visible), submit, error, update);
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
      for (const component of components) {
        const value = answers.get(component.question.id);
        if (value !== undefined) {
          this.answeredByCode.set(component.question.code, value);
        }
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
    const closeButton = h('button', { type: 'button', class: 'sw-submit' }, this.options.texts.closeButton);
    closeButton.addEventListener('click', () => this.dismiss());
    const thanks = h(
      'div',
      { class: 'sw-thanks' },
      h('p', { class: 'sw-thanks-text' }, this.options.texts.thankYou),
      h('div', { class: 'sw-actions' }, closeButton),
    );
    this.popup.classList.add('sw-finished');
    this.popup.replaceChildren(this.renderHeader(), thanks);
    if (this.options.thankYouAutoCloseMs > 0) {
      this.autoCloseTimer = setTimeout(() => this.dismiss(), this.options.thankYouAutoCloseMs);
    }
  }
}
