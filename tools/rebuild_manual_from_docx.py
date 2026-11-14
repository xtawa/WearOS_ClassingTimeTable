from pathlib import Path
from zipfile import ZipFile
from lxml import etree
from io import BytesIO
from xml.sax.saxutils import escape
import posixpath,json,re
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Image, Table, TableStyle, KeepTogether
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib import colors
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.lib.pagesizes import A4
import pymupdf as fitz
import pikepdf
from PIL import Image as PILImage

root=Path(__file__).resolve().parents[1]
folder=root/'软件著作权申请资料/正式资料'
src=folder/'Classing跨设备智能课程表软件_操作手册.docx'
out=folder/'Classing跨设备智能课程表软件_操作手册_重新排版.pdf'
pdfmetrics.registerFont(TTFont('CNBody','C:/Windows/Fonts/msyh.ttc',subfontIndex=0))
ns={'w':'http://schemas.openxmlformats.org/wordprocessingml/2006/main','a':'http://schemas.openxmlformats.org/drawingml/2006/main','r':'http://schemas.openxmlformats.org/officeDocument/2006/relationships'}
bodyStyle=ParagraphStyle('body',fontName='CNBody',fontSize=10,leading=17,spaceAfter=8,wordWrap='CJK',firstLineIndent=20)
headStyle=ParagraphStyle('heading',parent=bodyStyle,fontSize=14,leading=21,spaceBefore=12,spaceAfter=10,firstLineIndent=0,keepWithNext=True)
titleStyle=ParagraphStyle('title',parent=headStyle,fontSize=19,leading=29,spaceAfter=18)
cellStyle=ParagraphStyle('cell',parent=bodyStyle,fontSize=9,leading=14,firstLineIndent=0,spaceAfter=0)
story=[]; originals=[]; image_count=0
with ZipFile(src) as z:
    xml=etree.fromstring(z.read('word/document.xml'))
    relxml=etree.fromstring(z.read('word/_rels/document.xml.rels'))
    rels={x.get('Id'):x.get('Target') for x in relxml}
    def text(e):return ''.join(e.xpath('.//w:t/text()',namespaces=ns))
    def para(t,sty=bodyStyle):return Paragraph(escape(t).replace('\n','<br/>'),sty)
    for block in xml.find('w:body',ns):
        kind=etree.QName(block).localname
        if kind=='p':
            t=text(block).strip()
            if t:
                originals.append(t)
                isheading=len(t)<55 and ('、' in t[:5])
                style=titleStyle if len(originals)==1 else headStyle if isheading else bodyStyle
                story.append(para(t,style))
            for blip in block.xpath('.//a:blip',namespaces=ns):
                rid=blip.get('{'+ns['r']+'}embed')
                target=rels.get(rid,'')
                path=posixpath.normpath(posixpath.join('word',target))
                if path not in z.namelist() and target.lstrip('/') in z.namelist():path=target.lstrip('/')
                if path not in z.namelist():raise RuntimeError('Missing source image '+path)
                original=PILImage.open(BytesIO(z.read(path))).convert('RGBA')
                flattened=PILImage.new('RGB',original.size,'white')
                flattened.paste(original,mask=original.getchannel('A'))
                data=BytesIO();flattened.save(data,format='JPEG',quality=92);data.seek(0)
                img=Image(data)
                scale=min(460/img.imageWidth,500/img.imageHeight,1)
                img.drawWidth=img.imageWidth*scale;img.drawHeight=img.imageHeight*scale
                image_count+=1
                caption=para(f'图 {image_count}  原文档操作界面',cellStyle)
                story.append(KeepTogether([Spacer(1,8),img,Spacer(1,6),caption,Spacer(1,12)]))
        elif kind=='tbl':
            data=[]
            for tr in block.findall('w:tr',ns):
                row=[]
                for tc in tr.findall('w:tc',ns):
                    t='\n'.join(text(p) for p in tc.findall('w:p',ns))
                    originals.append(t);row.append(para(t,cellStyle))
                data.append(row)
            n=max(map(len,data)); widths=[(A4[0]-84)/n]*n
            if n==3:widths=[78,98,A4[0]-84-176]
            tab=Table(data,colWidths=widths,repeatRows=1,hAlign='LEFT')
            tab.setStyle(TableStyle([('GRID',(0,0),(-1,-1),.4,colors.HexColor('#cccccc')),('BACKGROUND',(0,0),(-1,0),colors.HexColor('#eeeeee')),('VALIGN',(0,0),(-1,-1),'TOP'),('LEFTPADDING',(0,0),(-1,-1),6),('RIGHTPADDING',(0,0),(-1,-1),6),('TOPPADDING',(0,0),(-1,-1),5),('BOTTOMPADDING',(0,0),(-1,-1),5)]))
            story.extend([tab,Spacer(1,12)])

def frame(c,doc):
    c.setFont('CNBody',8)
    c.drawString(42,A4[1]-28,'Classing跨设备智能课程表软件 V1.0 | 操作手册')
    c.drawRightString(A4[0]-42,25,f'第 {doc.page} 页')

doc=SimpleDocTemplate(str(out),pagesize=A4,rightMargin=42,leftMargin=42,topMargin=47,bottomMargin=43,title='Classing跨设备智能课程表软件 操作手册',author='张熙豪',pageCompression=1)
doc.build(story,onFirstPage=frame,onLaterPages=frame)
with pikepdf.open(out) as normalized:
    for obj in normalized.objects:
        if isinstance(obj,pikepdf.Dictionary):
            for key in ('/ca','/CA'):
                if key in obj:
                    assert float(obj[key])==1
                    del obj[key]
    normalized.save(out.with_suffix('.normalized.pdf'),force_version='1.3',object_stream_mode=pikepdf.ObjectStreamMode.disable)
out.with_suffix('.normalized.pdf').replace(out)
d=fitz.open(out)
assert d.metadata['format']=='PDF 1.3'
assert not d.is_encrypted and not d.is_repaired
alltext=''.join(re.sub(r'第\s*\d+\s*页','',p.get_text().replace('Classing跨设备智能课程表软件 V1.0 | 操作手册','')) for p in d)
def norm(t):return ''.join(t.split())
missing=[i for i,t in enumerate(originals) if norm(t) not in norm(alltext)]
assert not missing,missing
qa=root/'tmp/pdfs/rebuilt_manual';qa.mkdir(parents=True,exist_ok=True)
for i in sorted(set([0,len(d)//2,len(d)-1])):
    d[i].get_pixmap(matrix=fitz.Matrix(1,1)).save(str(qa/f'page-{i+1}.png'))
for p in d:
    p.get_pixmap(matrix=fitz.Matrix(.25,.25))
print(json.dumps({'pages':len(d),'images':image_count,'source_text_blocks':len(originals),'all_source_text_preserved':not missing,'bytes':out.stat().st_size,'format':d.metadata['format'],'preview_pages':[1,len(d)//2+1,len(d)]},ensure_ascii=True))
