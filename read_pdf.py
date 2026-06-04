import os
import PyPDF2

pdf_files = [f for f in os.listdir('.') if f.endswith('.pdf')]
if pdf_files:
    pdf_file = pdf_files[0]
    print(f"Reading {pdf_file}")
    reader = PyPDF2.PdfReader(pdf_file)
    text = ""
    for page in reader.pages:
        text += page.extract_text() + "\n"
    
    with open("pdf_text.txt", "w", encoding="utf-8") as f:
        f.write(text)
    print("Text extracted and saved to pdf_text.txt")
else:
    print("No PDF file found")
