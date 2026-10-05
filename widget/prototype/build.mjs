// Собирает интерактивный прототип опросов в один HTML-файл: dist/prototype/index.html.
// Внутри — настоящий собранный виджет (dist/survey-widget.iife.js) и заглушка сервиса с примерами опросов.
// Запуск: npm run prototype
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const bundle = readFileSync(resolve(here, '../dist/survey-widget.iife.js'), 'utf8');
if (bundle.includes('</script')) {
  throw new Error('Bundle contains </script and cannot be inlined');
}

const escapeAttr = (text) =>
  text.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

const frame = readFileSync(resolve(here, 'frame.html'), 'utf8').replace('/*BUNDLE*/', () => bundle);
const page = readFileSync(resolve(here, 'page.html'), 'utf8').replace('__FRAME__', () => escapeAttr(frame));

const outDir = resolve(here, '../dist/prototype');
mkdirSync(outDir, { recursive: true });
writeFileSync(resolve(outDir, 'index.html'), page);
console.log(`Prototype: ${resolve(outDir, 'index.html')}`);
