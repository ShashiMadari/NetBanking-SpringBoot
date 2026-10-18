package com.inetbank.banking.account;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;

@Service
public class MonthlyAccountStatementPdfService {

    private static final Color O_BANK_BLUE =
            new Color(20, 70, 130);

    private static final Color LIGHT_BLUE =
            new Color(232, 240, 250);

    private static final Color LIGHT_GRAY =
            new Color(245, 245, 245);

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    private static final Font TITLE_FONT =
            new Font(Font.HELVETICA, 19, Font.BOLD, O_BANK_BLUE);

    private static final Font SUBTITLE_FONT =
            new Font(Font.HELVETICA, 10, Font.NORMAL, Color.DARK_GRAY);

    private static final Font SECTION_FONT =
            new Font(Font.HELVETICA, 12, Font.BOLD, O_BANK_BLUE);

    private static final Font LABEL_FONT =
            new Font(Font.HELVETICA, 9, Font.BOLD, Color.DARK_GRAY);

    private static final Font VALUE_FONT =
            new Font(Font.HELVETICA, 9, Font.NORMAL, Color.BLACK);

    private static final Font TABLE_HEADER_FONT =
            new Font(Font.HELVETICA, 8, Font.BOLD, Color.WHITE);

    private static final Font TABLE_FONT =
            new Font(Font.HELVETICA, 8, Font.NORMAL, Color.BLACK);

    private static final BaseFont FOOTER_FONT = footerFont();

    public byte[] create(MonthlyAccountStatementData statement) {

        try (ByteArrayOutputStream output =
                new ByteArrayOutputStream()) {

            Document document = new Document(
                    PageSize.A4,
                    36,
                    36,
                    48,
                    48
            );

            PdfWriter writer = PdfWriter.getInstance(
                    document,
                    output
            );

            writer.setPageEvent(new StatementFooter());
            document.open();

            document.add(new Paragraph("O BANK", TITLE_FONT));

            Paragraph heading = new Paragraph(
                    "Monthly Account Statement",
                    new Font(
                            Font.HELVETICA,
                            14,
                            Font.BOLD,
                            Color.BLACK
                    )
            );

            heading.setSpacingBefore(4);
            document.add(heading);

            Paragraph period = new Paragraph(
                    "Statement month: " + statement.statementMonth(),
                    SUBTITLE_FONT
            );

            period.setSpacingBefore(4);
            period.setSpacingAfter(16);
            document.add(period);

            addAccountDetails(document, statement);
            addTransactions(document, statement);

            document.close();

            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to generate account statement PDF.",
                    exception
            );
        }
    }

    private void addAccountDetails(
            Document document,
            MonthlyAccountStatementData statement)
            throws Exception {

        document.add(sectionHeading("Account details"));

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[] { 1, 1 });
        table.setSpacingAfter(14);

        addKeyValue(table, "Customer name", statement.customerName());
        addKeyValue(table, "Account number", statement.accountNumber());
        addKeyValue(table, "Account type", statement.accountType());
        addKeyValue(table, "Account status", statement.accountStatus());
        addKeyValue(table, "Branch", value(statement.branchName()));
        addKeyValue(table, "IFSC code", value(statement.ifscCode()));
        addKeyValue(table, "Available balance",
                money(statement.availableBalance()));
        addKeyValue(table, "Book balance",
                money(statement.bookBalance()));

        document.add(table);
    }

    private void addTransactions(
            Document document,
            MonthlyAccountStatementData statement)
            throws Exception {

        document.add(sectionHeading("Transaction details"));

        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100);

        table.setWidths(new float[] {
                1.45f,
                1.35f,
                0.9f,
                1.25f,
                2.05f,
                1.0f
        });

        table.setHeaderRows(1);

        addHeader(table, "Date");
        addHeader(table, "Type");
        addHeader(table, "Entry");
        addHeader(table, "Amount");
        addHeader(table, "Reference");
        addHeader(table, "Status");

        if (statement.transactions().isEmpty()) {
            PdfPCell noTransactions = new PdfPCell(
                    new Phrase(
                            "No transactions were recorded in this statement month.",
                            TABLE_FONT
                    )
            );

            noTransactions.setColspan(6);
            noTransactions.setPadding(8);
            noTransactions.setHorizontalAlignment(
                    Element.ALIGN_CENTER
            );

            table.addCell(noTransactions);
        } else {
            for (AccountStatementResponse transaction
                    : statement.transactions()) {

                addCell(
                        table,
                        transaction.transactionDate()
                                .format(DATE_FORMAT)
                );

                addCell(table, transaction.transactionType());
                addCell(table, transaction.entrySide());
                addCell(table, money(transaction.amount()));
                addCell(table, transaction.referenceNumber());
                addCell(table, transaction.status());
            }
        }

        document.add(table);

        Paragraph note = new Paragraph(
                "This statement is generated electronically by O Bank. "
                        + "Please retain it for your records.",
                SUBTITLE_FONT
        );

        note.setSpacingBefore(14);
        document.add(note);
    }

    private Paragraph sectionHeading(String text) {
        Paragraph heading = new Paragraph(text, SECTION_FONT);
        heading.setSpacingBefore(8);
        heading.setSpacingAfter(7);
        return heading;
    }

    private void addKeyValue(
            PdfPTable table,
            String label,
            String text) {

        PdfPCell labelCell = new PdfPCell(
                new Phrase(label, LABEL_FONT)
        );

        labelCell.setPadding(7);
        labelCell.setBackgroundColor(LIGHT_BLUE);
        labelCell.setBorderColor(Color.WHITE);

        PdfPCell valueCell = new PdfPCell(
                new Phrase(value(text), VALUE_FONT)
        );

        valueCell.setPadding(7);
        valueCell.setBorderColor(Color.WHITE);

        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    private void addHeader(
            PdfPTable table,
            String text) {

        PdfPCell cell = new PdfPCell(
                new Phrase(text, TABLE_HEADER_FONT)
        );

        cell.setPadding(6);
        cell.setBackgroundColor(O_BANK_BLUE);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setBorderColor(Color.WHITE);

        table.addCell(cell);
    }

    private void addCell(
            PdfPTable table,
            String text) {

        PdfPCell cell = new PdfPCell(
                new Phrase(value(text), TABLE_FONT)
        );

        cell.setPadding(5);
        cell.setBorderColor(new Color(225, 225, 225));

        table.addCell(cell);
    }

    private String money(BigDecimal amount) {
        return "INR " + (amount == null
                ? "0.00"
                : amount.setScale(2).toPlainString());
    }

    private String value(String text) {
        return text == null || text.isBlank() ? "-" : text;
    }

    private static BaseFont footerFont() {
        try {
            return BaseFont.createFont(
                    BaseFont.HELVETICA,
                    BaseFont.WINANSI,
                    BaseFont.NOT_EMBEDDED
            );
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to load PDF footer font.",
                    exception
            );
        }
    }

    private static class StatementFooter
            extends PdfPageEventHelper {

        @Override
        public void onEndPage(
                PdfWriter writer,
                Document document) {

            PdfContentByte canvas = writer.getDirectContent();

            canvas.beginText();
            canvas.setFontAndSize(FOOTER_FONT, 8);
            canvas.setColorFill(Color.DARK_GRAY);

            canvas.showTextAligned(
                    Element.ALIGN_LEFT,
                    "O Bank - Monthly Account Statement",
                    document.left(),
                    24,
                    0
            );

            canvas.showTextAligned(
                    Element.ALIGN_RIGHT,
                    "Page " + writer.getPageNumber(),
                    document.right(),
                    24,
                    0
            );

            canvas.endText();
        }
    }
}