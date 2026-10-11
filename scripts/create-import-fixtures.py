from pathlib import Path
import json
from PIL import Image, ImageDraw, ImageFont
from reportlab.pdfgen import canvas
from reportlab.lib.pagesizes import landscape, A4
root = Path(__file__).resolve().parents[1] / "docs/global-adaptation/fixtures"
root.mkdir(parents=True, exist_ok=True)
cases = [
("weekly", [("Mathematics","Monday","09:00","10:00","A101","ALL"),("History","Wednesday","11:00","12:00","B2","ALL"),("Physics","Friday","14:00","15:30","Lab","ALL")], []),
("ab-weeks", [("Literature A","Monday","09:00","10:00","A1","ODD"),("Literature B","Monday","09:00","10:00","A1","EVEN")], ["A/B week anchor must be confirmed"]),
("lecture-lab", [("Chemistry lecture","Tuesday","10:00","11:00","Hall","ALL"),("Chemistry lab","Thursday","14:00","16:00","Lab 3","ALL")], ["Lecture and lab are separate meetings of one course"]),
("conflict", [("Math","Monday","09:00","10:30","A1","ALL"),("English","Monday","10:00","11:00","A2","ALL")], ["Time conflict must be visible"]),
("missing-time", [("Biology","Wednesday","09:00","?","Lab","ALL"),("Art","Friday","13:00","14:00","Studio","ALL")], ["Biology end time unknown; do not invent it"]),
]
fontpath = Path("C:/Windows/Fonts/segoeui.ttf")
font = ImageFont.truetype(str(fontpath),30)
small = ImageFont.truetype(str(fontpath),21)
manifest=[]
for i,(name,rows,warnings) in enumerate(cases,1):
 image=Image.new("RGB",(1400,900),"#ffffff")
 draw=ImageDraw.Draw(image)
 draw.text((60,55),"SYNTHETIC TEST "+str(i)+" / "+name,fill="#171a1e",font=font)
 draw.text((60,115),"Term: 2026-10-12 to 2026-12-20. School local time. Not real student data.",fill="#687078",font=small)
 headers=["Course","Day","Start","End","Room","Weeks"]
 x=[60,440,660,820,980,1170]
 for j,label in enumerate(headers): draw.text((x[j],205),label,fill="#3e5ed7",font=small)
 for index,row in enumerate(rows):
  y=275+index*105;draw.line((60,y-18,1330,y-18),fill="#dde2e1",width=2)
  for j,value in enumerate(row):draw.text((x[j],y),value,fill="#171a1e",font=small)
 draw.text((60,740),"Review warnings: "+"; ".join(warnings or ["none"]),fill="#b95b19",font=small)
 image.save(root/(str(i)+"-"+name+".png"))
 pdf=canvas.Canvas(str(root/(str(i)+"-"+name+".pdf")),pagesize=landscape(A4))
 pdf.drawImage(str(root/(str(i)+"-"+name+".png")),0,0,width=842,height=542)
 pdf.showPage();pdf.save()
 lessons=[]
 for row in rows:
  title,day,start,end,room,parity=row
  if end=="?":continue
  lessons.append(dict(id="sample-"+str(i)+"-"+str(len(lessons)),title=title,dayOfWeek=["Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday"].index(day)+1,startTime=start,endTime=end,location=room,startWeek=1,endWeek=10,weekParity=parity))
 expected=dict(lessons=lessons,warnings=warnings)
 (root/(str(i)+"-"+name+"-expected.json")).write_text(json.dumps(expected,indent=2),encoding="utf8")
 manifest.append(dict(case=name,image=str(i)+"-"+name+".png",pdf=str(i)+"-"+name+".pdf",expected=str(i)+"-"+name+"-expected.json",synthetic=True,aiAccuracyTested=False))
(root/"manifest.json").write_text(json.dumps(manifest,indent=2),encoding="utf8")
print("Created 5 synthetic screenshots, 5 PDFs and expected manual labels. No AI/provider validation claimed.")
