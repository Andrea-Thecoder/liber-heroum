-- Vincoli e indici che il generatore di Ebean non scrive nella V1.0.

-- game_character
alter table game_character add constraint ck_game_character_background_color check (background_color ~ '^#[0-9A-Fa-f]{6}$');

-- character_background
alter table character_background add constraint ck_character_background_text_length check (text_length >= 0);

-- character_sheet: un documento appartiene a una sola scheda
alter table character_sheet add constraint uq_character_sheet_document_id unique (document_id);

-- diary
alter table diary add constraint ck_diary_cover_color check (cover_color ~ '^#[0-9A-Fa-f]{6}$');

-- diary_page: numeri da 1 a 100, unici per diario ma controllati al commit (la rinumerazione dopo una cancellazione li scambia)
alter table diary_page add constraint ck_diary_page_page_number check (page_number between 1 and 100);

alter table diary_page drop constraint if exists uq_diary_page_diary_id_page_number;
alter table diary_page add constraint uq_diary_page_diary_id_page_number unique (diary_id, page_number) deferrable initially deferred;

-- diary_page_tag: il diario del tag e' sempre il diario della sua pagina (chiave esterna composta)
alter table diary_page add constraint uq_diary_page_id_diary_id unique (id, diary_id);
alter table diary_page_tag add constraint fk_diary_page_tag_page_id_diary_id foreign key (page_id, diary_id) references diary_page (id, diary_id) on delete cascade on update restrict;

create index ix_diary_page_tag_diary_id_lower_name on diary_page_tag (diary_id, lower(name));

create extension if not exists pg_trgm;
create index ix_diary_page_tag_name_trgm on diary_page_tag using gin (name gin_trgm_ops);

-- feedback
alter table feedback add constraint ck_feedback_privacy_accepted check (privacy_accepted = true);
alter table feedback add constraint ck_feedback_message_length check (char_length(message) between 1 and 2000);
alter table feedback_document add constraint uq_feedback_document_document_id unique (document_id);

create index if not exists ix_feedback_created_at on feedback (created_at);

-- payment
alter table payment add constraint ck_payment_amount_cents check (amount_cents >= 0);
alter table payment add constraint ck_payment_currency check (currency ~ '^[A-Z]{3}$');
alter table payment add constraint ck_payment_customer_country check (customer_country is null or customer_country ~ '^[A-Z]{2}$');

-- shedlock: lock dei job schedulati, cosi' con piu' istanze del backend un job parte una volta sola (ShedLock, JdbcTemplateLockProvider)
create table shedlock (
  name                          varchar(64) not null,
  lock_until                    timestamp not null,
  locked_at                     timestamp not null,
  locked_by                     varchar(255) not null,
  constraint pk_shedlock primary key (name)
);
