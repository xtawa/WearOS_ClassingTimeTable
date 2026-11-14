from pathlib import Path
import hashlib, json, math, re
from reportlab.pdfgen import canvas
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.lib.pagesizes import A4
import fitz

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'output/pdf'
OUT.mkdir(parents=True, exist_ok=True)
pdfmetrics.registerFont(TTFont('Source', 'C:/Windows/Fonts/msyh.ttc', subfontIndex=0))
modules = ['shared', 'client-common', 'mobile', 'app']
files = [p for m in modules for p in sorted((ROOT/m/'src/main').rglob('*')) if p.suffix in ('.kt', '.java')]
rows, manifest = [], []
for p in files:
    data = p.read_bytes()
    lines = data.decode('utf-8-sig').splitlines()
    manifest.append({'file':p.relative_to(ROOT).as_posix(), 'sha256':hashlib.sha256(data).hexdigest(), 'lines':len(lines)})
    for n, line in enumerate(lines, 1):
        if line.strip():
            rows.append((p.relative_to(ROOT).as_posix(), n, line.expandtabs(4)))
# Pages are defined over the complete, deterministic source sequence first.
page_count = max(1, len(rows)//55)
base, extra = divmod(len(rows), page_count)
pages, pos = [], 0
for i in range(page_count):
    size = base + (i < extra)
    pages.append(rows[pos:pos+size]); pos += size
selected = list(range(page_count)) if page_count <= 60 else list(range(30))+list(range(page_count-30,page_count))
export_rows = [r for i in selected for r in pages[i]]
secret_pattern = re.compile(r'(?:sk-[A-Za-z0-9_-]{20,}|gh[pousr]_[A-Za-z0-9]{20,}|-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----)')
assert not any(secret_pattern.search(r[2]) for r in export_rows), 'Potential embedded secret: review locally before export'
pdf = OUT/'ClassingTime_V1.1.4_source_review.pdf'
c = canvas.Canvas(str(pdf), pagesize=A4)
c.setTitle('ClassingTime 课程表 V1.1.4 - 源程序核对稿')
c.setAuthor('张熙豪')
w,h=A4
verification=[]
for output_page, idx in enumerate(selected,1):
    page=pages[idx]
    c.setFont('Source',10)
    c.drawString(32,h-30,'ClassingTime 课程表 V1.1.4')
    c.setFont('Source',8)
    c.drawRightString(w-32,h-30,f'源程序 第 {idx+1} 页 / 共 {page_count} 页')
    c.setFont('Source',6)
    c.drawString(32,h-45,page[0][0])
    c.drawString(32,h-55,page[-1][0])
    visual=[]
    for path,n,line in page:
        # Wrap only for display. Manifest retains the exact original file/line.
        chunk=''
        first=True
        for ch in line:
            if chunk and pdfmetrics.stringWidth(chunk+ch,'Source',7)>w-88:
                visual.append((str(n) if first else '',chunk)); first=False; chunk=''
            chunk+=ch
        visual.append((str(n) if first else '',chunk))
    leading=min(12.4,(h-110)/len(visual))
    font=min(7,leading*.8)
    assert font>=4.5, f'Page {idx+1} too dense'
    y=h-74
    for label,line in visual:
        c.setFillColorRGB(.45,.45,.45); c.setFont('Source',5.5)
        c.drawRightString(53,y,label)
        c.setFillColorRGB(0,0,0); c.setFont('Source',font)
        c.drawString(61,y,line); y-=leading
    c.setFont('Source',7)
    c.drawString(32,24,'著作权人：张熙豪 | 当前源码核对稿 | 未核定历史版本日期')
    c.drawRightString(w-32,24,f'交存页 {output_page}/{len(selected)}')
    c.showPage()
    verification.append({'pdf_page':output_page,'source_page':idx+1,'source_lines':len(page),'font_size':font,'first':page[0][:2],'last':page[-1][:2]})
c.save()
doc=fitz.open(pdf)
assert len(doc)==len(selected)==60
for p in doc:
    assert 'ClassingTime' in p.get_text()
    for block in p.get_text('dict')['blocks']:
        for line in block.get('lines',[]):
            for s in line['spans']:
                x0,y0,x1,y1=s['bbox']
                assert x0>=0 and x1<=w+1 and y0>=0 and y1<=h+1
for i in [0,29,30,59]:
    doc[i].get_pixmap(matrix=fitz.Matrix(1.5,1.5)).save(str(OUT/f'preview_{i+1:02}.png'))
(OUT/'source_manifest.json').write_text(json.dumps({'ordering':modules,'blank_lines_removed':True,'nonempty_source_lines':len(rows),'original_lines':sum(x['lines'] for x in manifest),'files':manifest,'pages':verification},ensure_ascii=False,indent=2),encoding='utf-8')
(OUT/'README.txt').write_text('当前工作区 Android/Wear OS 源程序核对稿。\n按 shared、client-common、mobile、app 模块及文件路径排序，删除空白行后形成完整分页，每页至少55行原始非空源码，提取连续前30页和后30页。长行仅作视觉折行，不增加源程序行数。\n未包含Apple端、测试、资源文件、构建产物、密钥文件或第三方依赖。\n软件名称、版本及2026-10-07完成日期的一致性尚待用户核对。本稿含当前未提交变更，不可视为历史版本归档证明。\nsource_manifest.json记录文件哈希及每页源码范围，供复核使用。\n',encoding='utf-8')
print(json.dumps({'pdf':str(pdf),'pages':len(doc),'total_source_pages':page_count,'nonempty_lines':len(rows),'min_source_lines_per_page':min(x['source_lines'] for x in verification),'min_font':min(x['font_size'] for x in verification)},ensure_ascii=False))
