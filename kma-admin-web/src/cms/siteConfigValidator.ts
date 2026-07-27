import Ajv2020, { type ErrorObject } from 'ajv/dist/2020'
import schema from '../../../src/main/resources/contracts/portal-site-config-v2.schema.json'
import type { PortalSiteConfigV2 } from './siteConfig'

const ajv = new Ajv2020({ allErrors: true, strict: true })
const validate = ajv.compile<PortalSiteConfigV2>(schema)

export interface SiteConfigValidation {
  valid: boolean
  issues: string[]
}

function issue(error: ErrorObject) {
  const location = error.instancePath || '配置根节点'
  return `${location} ${error.message || '不合法'}`
}

export function validatePortalSiteConfig(value: unknown): SiteConfigValidation {
  const valid = validate(value)
  return {
    valid: Boolean(valid),
    issues: valid ? [] : (validate.errors || []).map(issue),
  }
}
