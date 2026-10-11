CREATE TABLE organizer_applications (
                                        id BIGINT AUTO_INCREMENT PRIMARY KEY,

                                        user_id BIGINT NOT NULL,

                                        organization_name VARCHAR(150) NOT NULL,

                                        experience TEXT NOT NULL,

                                        document_type VARCHAR(20) NOT NULL,

                                        document_reference VARCHAR(255) NULL,

                                        status VARCHAR(30) NOT NULL DEFAULT 'PENDING_VERIFICATION',

                                        reviewed_by BIGINT NULL,

                                        rejection_reason VARCHAR(500) NULL,

                                        applied_at DATETIME(6) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(6),

                                        reviewed_at DATETIME(6) NULL,

                                        CONSTRAINT fk_organizer_application_user
                                            FOREIGN KEY (user_id)
                                                REFERENCES users(id),

                                        CONSTRAINT fk_organizer_application_reviewer
                                            FOREIGN KEY (reviewed_by)
                                                REFERENCES users(id),

                                        CONSTRAINT chk_organizer_document_type
                                            CHECK (document_type IN ('NID', 'PASSPORT')),

                                        CONSTRAINT chk_organizer_application_status
                                            CHECK (status IN (
                                                              'PENDING_VERIFICATION',
                                                              'VERIFIED',
                                                              'REJECTED'
                                                )),

                                        INDEX idx_organizer_application_user (user_id),

                                        INDEX idx_organizer_application_status (status)
);