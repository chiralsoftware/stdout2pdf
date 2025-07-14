package chiralsoftware.stdout2pdf;

import java.io.*;
import java.util.*;

public class SimplePDF {
    public static void main(String[] args) throws IOException {
        ByteArrayOutputStream pdfBody = new ByteArrayOutputStream();
        List<Integer> objectOffsets = new ArrayList<>();

        // PDF Header
        pdfBody.write("%PDF-1.4\n".getBytes());
        pdfBody.write("%âãïó\n".getBytes());  // Binary comment

        // Object 1: Catalog
        objectOffsets.add(pdfBody.size());
        pdfBody.write("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n".getBytes());

        // Object 2: Pages
        objectOffsets.add(pdfBody.size());
        pdfBody.write("2 0 obj\n<< /Type /Pages /Count 1 /Kids [3 0 R] >>\nendobj\n".getBytes());

        // Object 3: Page
        objectOffsets.add(pdfBody.size());
        pdfBody.write(
                "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>\nendobj\n".getBytes());

        // Object 4: Stream contents (text)
        objectOffsets.add(pdfBody.size());
        String stream = "BT\n/F1 12 Tf\n100 700 Td\n(Hello, PDF!) Tj\nET";
        byte[] streamBytes = stream.getBytes();
        pdfBody.write(("4 0 obj\n<< /Length " + streamBytes.length + " >>\nstream\n").getBytes());
        pdfBody.write(streamBytes);
        pdfBody.write("\nendstream\nendobj\n".getBytes());

        // Object 5: Font
        objectOffsets.add(pdfBody.size());
        pdfBody.write("5 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Courier >>\nendobj\n".getBytes());

        // XRef
        int xrefOffset = pdfBody.size();
        StringBuilder xref = new StringBuilder();
        xref.append("xref\n0 6\n");
        xref.append("0000000000 65535 f \n"); // Object 0: free
        for (int offset : objectOffsets) {
            xref.append(String.format("%010d 00000 n \n", offset));
        }

        // Trailer
        xref.append("trailer\n<< /Size 6 /Root 1 0 R >>\n");
        xref.append("startxref\n").append(xrefOffset).append("\n%%EOF");

        // Write to file
        try (FileOutputStream fos = new FileOutputStream("minimal.pdf")) {
            pdfBody.writeTo(fos);
            fos.write(xref.toString().getBytes());
        }

        System.out.println("PDF generated: minimal.pdf");
    }
}
