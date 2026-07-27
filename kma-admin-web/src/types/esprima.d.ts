declare module 'esprima' {
  export interface ParseOptions {
    loc?: boolean
    tolerant?: boolean
  }

  export function parseScript(
    code: string,
    options?: ParseOptions,
  ): { errors?: Array<{ description?: string; lineNumber?: number }> }
}
