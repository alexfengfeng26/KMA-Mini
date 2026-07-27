import { readFile } from 'node:fs/promises'
import { gzip } from 'node:zlib'
import { promisify } from 'node:util'
import path from 'node:path'

const gzipAsync = promisify(gzip)
const distDir = path.resolve('dist')
const manifest = JSON.parse(
  await readFile(path.join(distDir, '.vite', 'manifest.json'), 'utf8'),
)

async function gzipSize(file) {
  const content = await readFile(path.join(distDir, file))
  return (await gzipAsync(content)).byteLength
}

function collectStaticAssets(key, collected = new Set()) {
  if (collected.has(key)) return collected
  collected.add(key)
  const chunk = manifest[key]
  for (const imported of chunk?.imports || []) collectStaticAssets(imported, collected)
  return collected
}

const entryKey = Object.keys(manifest).find((key) => manifest[key].isEntry)
if (!entryKey) throw new Error('Vite manifest does not contain an entry chunk')

const initialKeys = [...collectStaticAssets(entryKey)]
const initialChunks = initialKeys.map((key) => manifest[key])
const initialJs = [
  ...new Set(initialChunks.map((chunk) => chunk.file).filter((file) => file.endsWith('.js'))),
]
const initialCss = [
  ...new Set(initialChunks.flatMap((chunk) => chunk.css || []).filter((file) => file.endsWith('.css'))),
]
const dynamicJs = (sourcePrefix) =>
  Object.entries(manifest)
    .filter(
      ([source, chunk]) =>
        source.startsWith(sourcePrefix) && chunk.isDynamicEntry && chunk.file.endsWith('.js'),
    )
    .map(([, chunk]) => chunk.file)
const routeJs = dynamicJs('src/views/')
const blockJs = dynamicJs('src/cms/blocks/')

const sumGzip = async (files) => {
  const sizes = await Promise.all(files.map(gzipSize))
  return sizes.reduce((total, size) => total + size, 0)
}

const results = {
  entryJsGzip: await sumGzip(initialJs),
  entryCssGzip: await sumGzip(initialCss),
  largestRouteGzip: Math.max(0, ...(await Promise.all(routeJs.map(gzipSize)))),
  largestBlockGzip: Math.max(0, ...(await Promise.all(blockJs.map(gzipSize)))),
  runtimeConfigBytes: (
    await readFile(path.resolve('public', 'config', 'kma-runtime.json'))
  ).byteLength,
}

const budgets = {
  entryJsGzip: 140 * 1024,
  entryCssGzip: 22 * 1024,
  largestRouteGzip: 40 * 1024,
  largestBlockGzip: 40 * 1024,
  runtimeConfigBytes: 50 * 1024,
}

for (const [metric, value] of Object.entries(results)) {
  const limit = budgets[metric]
  console.log(`${metric}: ${(value / 1024).toFixed(2)} KiB / ${(limit / 1024).toFixed(0)} KiB`)
  if (value > limit) process.exitCode = 1
}

const forbiddenInitialEntries = [
  'PortalAppearanceView',
  'DashboardView',
  'UsersView',
  'cms-news/template',
  'reading-focus/template',
]
const accidentallyInitial = initialKeys.filter((key) =>
  forbiddenInitialEntries.some((pattern) => key.includes(pattern)),
)
if (accidentallyInitial.length) {
  process.exitCode = 1
  console.error(`Unexpected initial console/template chunks: ${accidentallyInitial.join(', ')}`)
}

if (process.exitCode) throw new Error('Frontend bundle budget exceeded')
