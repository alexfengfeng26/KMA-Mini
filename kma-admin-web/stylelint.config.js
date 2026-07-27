export default {
  extends: ['stylelint-config-standard-vue'],
  ignores: ['dist/**', 'node_modules/**', 'src/api/generated/**'],
  rules: {
    'color-function-notation': 'modern',
    'alpha-value-notation': 'number',
    'selector-class-pattern': null,
    'custom-property-pattern': null,
    'declaration-empty-line-before': null,
  },
}
