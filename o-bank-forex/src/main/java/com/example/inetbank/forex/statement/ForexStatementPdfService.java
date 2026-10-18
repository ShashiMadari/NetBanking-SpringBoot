package com.example.inetbank.forex.statement;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Service;

import com.example.inetbank.forex.conversion.ForexTransactionResponse;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

@Service
public class ForexStatementPdfService {

    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM uuuu HH:mm");

    public byte[] create(
            ForexTransactionStatementResponse statement) {

        try (ByteArrayOutputStream output =
                new ByteArrayOutputStream()) {

            Document document = new Document(
                    PageSize.A4.rotate(),
                    24,
                    24,
                    28,
                    28
            );

            PdfWriter.getInstance(document, output);
            document.open();

            addHeader(document, statement);
            addSummary(document, statement);
            addTransactions(document, statement);

            document.close();

            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Forex statement PDF could not be generated.",
                    exception
            );
        }
    }

    private void addHeader(
            Document document,
            ForexTransactionStatementResponse statement)
            throws Exception {

        Paragraph title = new Paragraph(
                "O Bank - Forex Transaction Statement",
                font(18, Font.BOLD, new Color(25, 64, 120))
        );

        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(8);
        document.add(title);

        Paragraph period = new Paragraph(
                "Statement period: "
                        + statement.statementFrom()
                        + " to "
                        + statement.statementTo(),
                font(10, Font.NORMAL, Color.DARK_GRAY)
        );

        period.setAlignment(Element.ALIGN_CENTER);
        period.setSpacingAfter(16);
        document.add(period);
    }

    private void addSummary(
            Document document,
            ForexTransactionStatementResponse statement)
            throws Exception {

        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1.2f, 1.4f, 1.2f, 1.4f});
        table.setSpacingAfter(16);

        addSummaryCell(table, "Period", statement.period().name());
        addSummaryCell(
                table,
                "Transactions",
                String.valueOf(statement.totalTransactions())
        );
        addSummaryCell(
                table,
                "Page",
                (statement.page() + 1)
                        + " of "
                        + Math.max(statement.totalPages(), 1)
        );
        addSummaryCell(
                table,
                "Generated",
                java.time.LocalDate.now().toString()
        );

        document.add(table);
    }

    private void addTransactions(
            Document document,
            ForexTransactionStatementResponse statement)
            throws Exception {

        Paragraph heading = new Paragraph(
                "Transaction Activity",
                font(13, Font.BOLD, new Color(25, 64, 120))
        );

        heading.setSpacingAfter(8);
        document.add(heading);

        PdfPTable table = new PdfPTable(8);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{
                1.0f, 1.35f, 1.0f, 1.0f,
                1.0f, 1.05f, 1.5f, 1.45f
        });

        addHeaderCell(table, "Date");
        addHeaderCell(table, "Type");
        addHeaderCell(table, "From");
        addHeaderCell(table, "To");
        addHeaderCell(table, "Amount");
        addHeaderCell(table, "INR Value");
        addHeaderCell(table, "Reference");
        addHeaderCell(table, "Status");

        if (statement.transactions().isEmpty()) {
            PdfPCell cell = new PdfPCell(new Paragraph(
                    "No Forex transactions were found "
                            + "for this statement period.",
                    font(10, Font.NORMAL, Color.DARK_GRAY)
            ));

            cell.setColspan(8);
            cell.setPadding(12);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        } else {
            for (ForexTransactionResponse transaction
                    : statement.transactions()) {

                addTransactionRow(table, transaction);
            }
        }

        document.add(table);

        Paragraph footer = new Paragraph(
                "\nThis is an automated O Bank Forex statement. "
                        + "Please do not reply to this email.",
                font(9, Font.ITALIC, Color.DARK_GRAY)
        );

        footer.setAlignment(Element.ALIGN_CENTER);
        document.add(footer);
    }

    private void addTransactionRow(
            PdfPTable table,
            ForexTransactionResponse transaction) {

        addBodyCell(
                table,
                transaction.createdAt()
                        .format(DATE_TIME_FORMAT)
        );

        addBodyCell(table, value(transaction.transactionType()));
        addBodyCell(table, value(transaction.fromCurrency()));
        addBodyCell(table, value(transaction.toCurrency()));

        String amount = transaction.fromAmount() != null
                ? transaction.fromAmount().toPlainString()
                : transaction.receiveAmount() == null
                        ? "-"
                        : transaction.receiveAmount().toPlainString();

        addBodyCell(table, amount);

        addBodyCell(
                table,
                transaction.inrEquivalent() == null
                        ? "-"
                        : "INR "
                                + transaction.inrEquivalent()
                                        .toPlainString()
        );

        addBodyCell(table, value(transaction.referenceNumber()));
        addBodyCell(table, value(transaction.status()));
    }

    private void addSummaryCell(
            PdfPTable table,
            String label,
            String value) {

        PdfPCell cell = new PdfPCell();

        cell.setPadding(8);
        cell.setBackgroundColor(new Color(239, 246, 255));

        Paragraph labelText = new Paragraph(
                label,
                font(8, Font.BOLD, new Color(25, 64, 120))
        );

        Paragraph valueText = new Paragraph(
                value,
                font(10, Font.NORMAL, Color.BLACK)
        );

        cell.addElement(labelText);
        cell.addElement(valueText);

        table.addCell(cell);
    }

    private void addHeaderCell(
            PdfPTable table,
            String text) {

        PdfPCell cell = new PdfPCell(new Paragraph(
                text,
                font(8, Font.BOLD, Color.WHITE)
        ));

        cell.setBackgroundColor(new Color(25, 64, 120));
        cell.setPadding(6);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);

        table.addCell(cell);
    }

    private void addBodyCell(
            PdfPTable table,
            String text) {

        PdfPCell cell = new PdfPCell(new Paragraph(
                value(text),
                font(8, Font.NORMAL, Color.BLACK)
        ));

        cell.setPadding(5);
        table.addCell(cell);
    }

    private Font font(
            float size,
            int style,
            Color color) {

        return FontFactory.getFont(
                FontFactory.HELVETICA,
                size,
                style,
                color
        );
    }

    private String value(String value) {
        return value == null || value.isBlank()
                ? "-"
                : value;
    }
}