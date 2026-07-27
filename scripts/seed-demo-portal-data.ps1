[CmdletBinding()]
param(
    [string]$HostName = 'localhost',
    [int]$Port = 5432,
    [string]$UserName = 'postgres',
    [string]$Database = 'kma_mini',
    [string]$PsqlPath = 'C:\Program Files\PostgreSQL\18\bin\psql.exe'
)

$ErrorActionPreference = 'Stop'

if ($Database -cne 'kma_mini') {
    throw "安全保护：演示门户数据只能写入 kma_mini，当前目标为 $Database。"
}

if (-not (Test-Path -LiteralPath $PsqlPath -PathType Leaf)) {
    throw "未找到 psql：$PsqlPath。请通过 -PsqlPath 提供 PostgreSQL 18 的 psql.exe 路径。"
}

if ([string]::IsNullOrWhiteSpace($env:PGPASSWORD)) {
    $password = Read-Host 'PostgreSQL 密码（仅用于本次进程）' -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($password)
    try {
        $env:PGPASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    }
}

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$seedFile = Join-Path $scriptRoot 'sql\seed-demo-portal-data.sql'
if (-not (Test-Path -LiteralPath $seedFile -PathType Leaf)) {
    throw "未找到种子 SQL：$seedFile"
}

$databaseName = & $PsqlPath -h $HostName -p $Port -U $UserName -d $Database -X -At -v ON_ERROR_STOP=1 -c 'SELECT current_database()'
if ($LASTEXITCODE -ne 0 -or $databaseName.Trim() -cne 'kma_mini') {
    throw '安全保护：无法确认当前连接为 kma_mini，未执行任何写入。'
}

$precheck = & $PsqlPath -h $HostName -p $Port -U $UserName -d $Database -X -At -v ON_ERROR_STOP=1 -c @'
SELECT
  EXISTS (SELECT 1 FROM knowledge_space WHERE space_code='default' AND status='active') AS has_default_space,
  EXISTS (SELECT 1 FROM knowledge_doc WHERE source_tag IS DISTINCT FROM 'demo-portal') AS has_non_demo_documents;
'@
if ($LASTEXITCODE -ne 0) {
    throw '无法完成演示数据前置检查。'
}
if ($precheck.Trim() -ne 't|f') {
    throw "安全保护：需要活跃的 default 空间且不能存在非 demo-portal 文档，当前检查结果为 $($precheck.Trim())。"
}

& $PsqlPath -h $HostName -p $Port -U $UserName -d $Database -X -v ON_ERROR_STOP=1 -f $seedFile
if ($LASTEXITCODE -ne 0) {
    throw '演示数据事务失败，PostgreSQL 已回滚。'
}

& $PsqlPath -h $HostName -p $Port -U $UserName -d $Database -X -v ON_ERROR_STOP=1 -c @'
SELECT
  count(*) FILTER (WHERE source_tag='demo-portal') AS demo_documents,
  (SELECT count(*) FROM knowledge_chunk c JOIN knowledge_doc d ON d.doc_id=c.doc_id WHERE d.source_tag='demo-portal') AS demo_chunks,
  (SELECT count(*) FROM knowledge_favorite f JOIN knowledge_doc d ON d.doc_id=f.doc_id WHERE d.source_tag='demo-portal') AS demo_favorites,
  (SELECT count(*) FROM knowledge_read_history h JOIN knowledge_doc d ON d.doc_id=h.doc_id WHERE d.source_tag='demo-portal') AS demo_history
FROM knowledge_doc;
'@
if ($LASTEXITCODE -ne 0) {
    throw '种子完成，但无法读取验证统计。'
}
