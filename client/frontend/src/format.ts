export const rub = (kopecks: number) => {
  const whole = kopecks % 100 === 0;
  return `${(kopecks / 100).toLocaleString('ru-RU', {
    minimumFractionDigits: whole ? 0 : 2,
    maximumFractionDigits: 2,
  })} ₽`;
};

export const pct = (v: number) => `${String(v).replace('.', ',')}%`;

export function ruDate(s: string) {
  if (s.length === 10) {
    const [y, m, d] = s.split('-');
    return `${d}.${m}.${y}`;
  }
  return new Date(s).toLocaleDateString('ru-RU');
}