import type { DemoUser } from '../types';

const raw: (Omit<DemoUser, 'company'> & { company: { name: string; inn: string } | null })[] = [
  {
    id: 'coffee',
    fio: 'Смирнова А. В.',
    role: 'CLIENT',
    personaTitle: 'ООО «Кофейня» — автоодобрение',
    company: { name: 'ООО «Кофейня»', inn: '7701234560' },
  },
  {
    id: 'builder',
    fio: 'Петров С. Н.',
    role: 'CLIENT',
    personaTitle: 'ИП Петров (строитель) — андеррайтер и дозапрос',
    company: { name: 'ИП Петров С. Н.', inn: '590234567822' },
  },
  {
    id: 'wb',
    fio: 'Кузнецова М. И.',
    role: 'CLIENT',
    personaTitle: 'ИП Кузнецова (продавец на WB) — иные условия',
    company: { name: 'ИП Кузнецова М. И.', inn: '590987654351' },
  },
  {
    id: 'techsnab',
    fio: 'Орлов Д. А.',
    role: 'CLIENT',
    personaTitle: 'ООО «Техснаб» — долг в ФНС',
    company: { name: 'ООО «Техснаб»', inn: '7725555556' },
  },
  {
    id: 'shell',
    fio: 'Иванов И. И.',
    role: 'CLIENT',
    personaTitle: 'ООО «Однодневка» — отказ',
    company: { name: 'ООО «Однодневка»', inn: '7719999994' },
  },
  {
    id: 'underwriter',
    fio: 'Андеррайтер Демо',
    role: 'UNDERWRITER',
    personaTitle: null,
    company: null,
  },
];

export const demoUsers: DemoUser[] = raw.map((u) => ({
  ...u,
  company: u.company && {
    id: `company-${u.id}`,
    inn: u.company.inn,
    name: u.company.name,
    legalForm: u.company.name.startsWith('ИП') ? 'IP' : 'OOO',
  },
}));