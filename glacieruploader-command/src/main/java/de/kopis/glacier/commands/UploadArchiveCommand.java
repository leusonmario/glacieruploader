package de.kopis.glacier.commands;

/*
 * #%L
 * glacieruploader-command
 * %%
 * Copyright (C) 2012 - 2016 Carsten Ringe
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the 
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public 
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

import com.amazonaws.event.ProgressTracker;
import com.amazonaws.services.glacier.AmazonGlacier;
import com.amazonaws.services.glacier.transfer.ArchiveTransferManager;
import com.amazonaws.services.glacier.transfer.ArchiveTransferManagerBuilder;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sqs.AmazonSQS;
import de.kopis.glacier.parsers.GlacierUploaderOptionParser;
import joptsimple.OptionSet;

import java.io.File;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class UploadArchiveCommand extends AbstractCommand {

    private final ArchiveTransferManager atm;

    public UploadArchiveCommand(final AmazonGlacier client, final AmazonSQS sqs, final AmazonSNS sns) {
        this(client, sqs, sns, new ArchiveTransferManagerBuilder()
                .withGlacierClient(client)
                .withSqsClient(sqs)
                .withSnsClient(sns)
                .build());
    }

    public UploadArchiveCommand(final AmazonGlacier client, final AmazonSQS sqs, final AmazonSNS sns, final ArchiveTransferManager atm) {
        super(client, sqs, sns);
        this.atm = atm;
    }

    public void upload(final String vaultName, final File uploadFile) {
        log.info("Starting to upload {} to vault {}...", uploadFile, vaultName);
        final String sameAccountId = "-";
        final ProgressTracker progressTracker = new ProgressTracker();
        Executors.newScheduledThreadPool(1).scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                log.info("{}/{} transferred",
                        progressTracker.getProgress().getRequestBytesTransferred(),
                        progressTracker.getProgress().getRequestContentLength()
                );
            }
        },1000, 1000, TimeUnit.MILLISECONDS);
        final String archiveId = atm.upload(sameAccountId, vaultName, uploadFile.getName(), uploadFile,
                progressTracker).getArchiveId();
        log.info("Uploaded archive {}", archiveId);
    }

    @Override
    public void exec(OptionSet options, GlacierUploaderOptionParser optionParser) {
        final String vaultName = options.valueOf(optionParser.vault);
        final List<File> optionsFiles = options.valuesOf(optionParser.upload);
        final List<String> nonOptions = options.nonOptionArguments();
        final List<File> files = optionParser.mergeNonOptionsFiles(optionsFiles, nonOptions);

        for (File uploadFile : files) {
            this.upload(vaultName, uploadFile);
        }
    }

    @Override
    public boolean valid(OptionSet options, GlacierUploaderOptionParser optionParser) {
        return options.has(optionParser.upload);
    }
}
