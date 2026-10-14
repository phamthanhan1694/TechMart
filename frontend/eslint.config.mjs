// Flat config. Tu eslint-config-next 16, cac preset export thang ra mang flat
// config - khong con phai boc qua FlatCompat cua @eslint/eslintrc nhu truoc.
import coreWebVitals from 'eslint-config-next/core-web-vitals'
import typescript from 'eslint-config-next/typescript'

/** @type {import('eslint').Linter.Config[]} */
const eslintConfig = [
  ...coreWebVitals,
  ...typescript,

  {
    rules: {
      /*
       * TAT CO CHU DICH, khong phai vi ngai sua.
       *
       * Rule nay (moi tu Next 16 / React Compiler) canh bao viec goi setState
       * ngay trong than effect, vi no gay them mot luot render noi tiep. Khuyen
       * nghi chinh thuc cua React la dung fetch du lieu trong effect nua, ma de
       * framework hoac mot thu vien cache (SWR, React Query) lo.
       *
       * O day khong lam vay duoc: access token nam trong localStorage nen moi
       * loi goi API deu phai chay o phia client, khong dat len Server Component
       * duoc. Con lai dung mau `setLoading(true)` roi fetch - 27 cho.
       *
       * Day la rule ve HIEU NANG, khong phai tinh dung dan: hau qua toi da la
       * mot luot render thua cho moi lan doi bo loc. Doi lai, sua het 27 cho ma
       * khong doi kien truc chi la viet vong vo de qua mat linter.
       *
       * Doi quyet dinh nay khi: them SWR/React Query lam tang du lieu, hoac
       * chuyen sang co che luu token cho phep fetch phia server (cookie HttpOnly).
       * Luc do bat lai rule va sua that.
       */
      'react-hooks/set-state-in-effect': 'off',
    },
  },

  {
    ignores: ['.next/**', 'node_modules/**', 'next-env.d.ts'],
  },
]

export default eslintConfig
