package com.example.inetbank.credit.loan;

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
public class LoanStatementPdfService {

    private static final Color O_BANK_BLUE =
            new Color(20, 70, 130);

    private static final Color LIGHT_BLUE =
            new Color(232, 240, 250);

    private static final Color LIGHT_GRAY =
            new Color(245, 245, 245);

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy");

    private static final DateTimeFormatter DATE_TIME_FORMAT =
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

    public byte[] create(LoanMonthlyStatementResponse statement) {

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
                    "Loan Monthly Statement",
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
                    "Statement period: "
                            + statement.statementFrom()
                                    .format(DATE_FORMAT)
                            + " to "
                            + statement.statementTo()
                                    .format(DATE_FORMAT),
                    SUBTITLE_FONT
            );

            period.setSpacingBefore(4);
            period.setSpacingAfter(16);
            document.add(period);

            addLoanDetails(document, statement);
            addPaymentSummary(document, statement);
            addRepayments(document, statement);

            document.close();

            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to generate loan statement PDF.",
                    exception
            );
        }
    }

    private void addLoanDetails(
            Document document,
            LoanMonthlyStatementResponse statement)
            throws Exception {

        document.add(sectionHeading("Loan details"));

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[] { 1, 1 });
        table.setSpacingAfter(14);

        addKeyValue(
                table,
                "Loan account number",
                statement.loanAccountNumber()
        );

        addKeyValue(table, "Loan type", statement.loanType());

        addKeyValue(
                table,
                "Original loan amount",
                money(statement.originalLoanAmount())
        );

        addKeyValue(
                table,
                "Current outstanding",
                money(statement.currentOutstandingAmount())
        );

        addKeyValue(
                table,
                "Annual interest rate",
                statement.annualInterestRate()
                        .stripTrailingZeros()
                        .toPlainString()
                        + "% p.a."
        );

        addKeyValue(
                table,
                "Current EMI amount",
                money(statement.emiAmount())
        );

        addKeyValue(
                table,
                "Next EMI due date",
                statement.nextDueDate() == null
                        ? "Not applicable"
                        : statement.nextDueDate()
                                .format(DATE_FORMAT)
        );

        addKeyValue(table, "Loan status", statement.loanStatus());

        document.add(table);
    }

    private void addPaymentSummary(
            Document document,
            LoanMonthlyStatementResponse statement)
            throws Exception {

        document.add(sectionHeading("Monthly payment summary"));

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[] { 3, 1 });
        table.setSpacingAfter(14);

        addSummaryRow(
                table,
                "EMI payments",
                money(statement.totalEmiPayments())
        );

        addSummaryRow(
                table,
                "Part payments",
                money(statement.totalPartPayments())
        );

        addSummaryRow(
                table,
                "Foreclosure payments",
                money(statement.totalForeclosures())
        );

        addSummaryRow(
                table,
                "Number of repayments",
                String.valueOf(statement.entries().size())
        );

        document.add(table);
    }

    private void addRepayments(
            Document document,
            LoanMonthlyStatementResponse statement)
            throws Exception {

        document.add(sectionHeading("Repayment details"));

        PdfPTable table = new PdfPTable(7);
        table.setWidthPercentage(100);

        table.setWidths(new float[] {
                1.2f,
                1.1f,
                1.75f,
                1.15f,
                1.15f,
                1.15f,
                1.3f
        });

        table.setHeaderRows(1);

        addHeader(table, "Date");
        addHeader(table, "Type");
        addHeader(table, "Reference");
        addHeader(table, "Amount");
        addHeader(table, "Principal");
        addHeader(table, "Interest");
        addHeader(table, "Outstanding");

        if (statement.entries().isEmpty()) {
            PdfPCell noPayments = new PdfPCell(
                    new Phrase(
                            "No loan repayments were recorded in this statement period.",
                            TABLE_FONT
                    )
            );

            noPayments.setColspan(7);
            noPayments.setPadding(8);
            noPayments.setHorizontalAlignment(Element.ALIGN_CENTER);

            table.addCell(noPayments);
        } else {
            for (LoanMonthlyStatementEntryResponse entry
                    : statement.entries()) {

                addCell(
                        table,
                        entry.paymentDate()
                                .format(DATE_TIME_FORMAT)
                );

                addCell(table, entry.repaymentType());
                addCell(table, entry.referenceNumber());
                addCell(table, money(entry.amount()));
                addCell(table, money(entry.principalComponent()));
                addCell(table, money(entry.interestComponent()));
                addCell(table, money(entry.outstandingAfter()));
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

    private Paragraph sectionHeading(String value) {
        Paragraph heading = new Paragraph(value, SECTION_FONT);
        heading.setSpacingBefore(8);
        heading.setSpacingAfter(7);
        return heading;
    }

    private void addKeyValue(
            PdfPTable table,
            String label,
            String value) {

        PdfPCell labelCell = new PdfPCell(
                new Phrase(label, LABEL_FONT)
        );

        labelCell.setPadding(7);
        labelCell.setBackgroundColor(LIGHT_BLUE);
        labelCell.setBorderColor(Color.WHITE);

        PdfPCell valueCell = new PdfPCell(
                new Phrase(value, VALUE_FONT)
        );

        valueCell.setPadding(7);
        valueCell.setBorderColor(Color.WHITE);

        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    private void addSummaryRow(
            PdfPTable table,
            String label,
            String value) {

        PdfPCell labelCell = new PdfPCell(
                new Phrase(label, VALUE_FONT)
        );

        labelCell.setPadding(6);
        labelCell.setBackgroundColor(LIGHT_GRAY);
        labelCell.setBorderColor(Color.WHITE);

        PdfPCell valueCell = new PdfPCell(
                new Phrase(value, LABEL_FONT)
        );

        valueCell.setPadding(6);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        valueCell.setBackgroundColor(LIGHT_GRAY);
        valueCell.setBorderColor(Color.WHITE);

        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    private void addHeader(
            PdfPTable table,
            String value) {

        PdfPCell cell = new PdfPCell(
                new Phrase(value, TABLE_HEADER_FONT)
        );

        cell.setPadding(6);
        cell.setBackgroundColor(O_BANK_BLUE);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setBorderColor(Color.WHITE);

        table.addCell(cell);
    }

    private void addCell(
            PdfPTable table,
            String value) {

        PdfPCell cell = new PdfPCell(
                new Phrase(
                        value == null ? "-" : value,
                        TABLE_FONT
                )
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
                    "O Bank - Loan Monthly Statement",
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