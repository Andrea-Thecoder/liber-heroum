-- apply changes
create table app_user (
  id                            uuid not null,
  is_premium                    boolean default false not null,
  avatar_document_id            uuid,
  created_at                    timestamptz not null,
  updated_at                    timestamptz not null,
  version                       bigint not null,
  subject                       varchar(255) not null,
  email                         varchar(255),
  settings                      jsonb,
  created_by                    varchar not null,
  updated_by                    varchar not null,
  constraint uq_app_user_subject unique (subject),
  constraint pk_app_user primary key (id)
);

create table character_background (
  id                            uuid not null,
  character_id                  uuid not null,
  text_length                   integer not null,
  created_at                    timestamptz not null,
  updated_at                    timestamptz not null,
  version                       bigint not null,
  content                       jsonb not null,
  preview                       varchar(300) not null,
  created_by                    varchar not null,
  updated_by                    varchar not null,
  constraint uq_character_background_character_id unique (character_id),
  constraint pk_character_background primary key (id)
);

create table character_sheet (
  id                            uuid not null,
  character_id                  uuid not null,
  document_id                   uuid not null,
  created_at                    timestamptz not null,
  updated_at                    timestamptz not null,
  version                       bigint not null,
  title                         varchar(100) not null,
  game_system                   varchar(100),
  created_by                    varchar not null,
  updated_by                    varchar not null,
  constraint pk_character_sheet primary key (id)
);

create table diary (
  id                            uuid not null,
  font_size                     smallint,
  character_id                  uuid not null,
  cover_document_id             uuid,
  bookmark_page_id              uuid,
  created_at                    timestamptz not null,
  updated_at                    timestamptz not null,
  version                       bigint not null,
  title                         varchar(255) not null,
  description                   varchar(2000),
  cover_color                   varchar(7),
  font                          varchar(100),
  created_by                    varchar not null,
  updated_by                    varchar not null,
  constraint pk_diary primary key (id)
);

create table diary_page (
  id                            uuid not null,
  page_number                   smallint not null,
  diary_id                      uuid not null,
  created_at                    timestamptz not null,
  updated_at                    timestamptz not null,
  version                       bigint not null,
  content                       jsonb not null,
  created_by                    varchar not null,
  updated_by                    varchar not null,
  constraint uq_diary_page_diary_id_page_number unique (diary_id,page_number),
  constraint pk_diary_page primary key (id)
);

create table diary_page_tag (
  id                            uuid not null,
  diary_id                      uuid not null,
  page_id                       uuid not null,
  created_at                    timestamptz not null,
  updated_at                    timestamptz not null,
  version                       bigint not null,
  name                          varchar(30) not null,
  created_by                    varchar not null,
  updated_by                    varchar not null,
  constraint uq_diary_page_tag_page_id unique (page_id),
  constraint pk_diary_page_tag primary key (id)
);

create table document (
  id                            uuid not null,
  size                          bigint not null,
  created_at                    timestamptz not null,
  updated_at                    timestamptz not null,
  version                       bigint not null,
  name                          varchar(255) not null,
  mime_type                     varchar(127) not null,
  bucket                        varchar(63) not null,
  object_key                    varchar(1024) not null,
  created_by                    varchar not null,
  updated_by                    varchar not null,
  constraint pk_document primary key (id)
);

create table feedback (
  id                            uuid not null,
  privacy_accepted              boolean default false not null,
  message                       text not null,
  created_at                    timestamptz not null,
  updated_at                    timestamptz not null,
  version                       bigint not null,
  type                          varchar(20) not null,
  status                        varchar(20) not null,
  email                         varchar(254) not null,
  technical_info                jsonb,
  created_by                    varchar not null,
  updated_by                    varchar not null,
  constraint ck_feedback_type check ( type in ('IDEA','BUG','LOVE')),
  constraint ck_feedback_status check ( status in ('NEW','READ','ANSWERED','ARCHIVED')),
  constraint pk_feedback primary key (id)
);

create table feedback_document (
  id                            uuid not null,
  feedback_id                   uuid not null,
  document_id                   uuid not null,
  created_at                    timestamptz not null,
  updated_at                    timestamptz not null,
  version                       bigint not null,
  created_by                    varchar not null,
  updated_by                    varchar not null,
  constraint pk_feedback_document primary key (id)
);

create table game_character (
  id                            uuid not null,
  owner_id                      uuid not null,
  avatar_document_id            uuid,
  created_at                    timestamptz not null,
  updated_at                    timestamptz not null,
  version                       bigint not null,
  name                          varchar(255) not null,
  campaign_name                 varchar(255),
  background_color              varchar(7),
  kind                          varchar(20) not null,
  created_by                    varchar not null,
  updated_by                    varchar not null,
  constraint ck_game_character_kind check ( kind in ('PLAYER','GAME_MASTER')),
  constraint pk_game_character primary key (id)
);

create table payment (
  id                            uuid not null,
  app_user_id                   uuid,
  amount_cents                  bigint not null,
  paid_at                       timestamptz,
  retain_until                  date not null,
  created_at                    timestamptz not null,
  updated_at                    timestamptz not null,
  version                       bigint not null,
  provider                      varchar(30) not null,
  provider_payment_id           varchar(255) not null,
  status                        varchar(20) not null,
  currency                      varchar(3) not null,
  description                   varchar(255) not null,
  customer_email                varchar(254) not null,
  customer_name                 varchar(255),
  customer_tax_code             varchar(32),
  customer_country              varchar(2),
  invoice_number                varchar(64),
  receipt_url                   varchar(2048),
  created_by                    varchar not null,
  updated_by                    varchar not null,
  constraint ck_payment_provider check ( provider in ('STRIPE')),
  constraint ck_payment_status check ( status in ('PENDING','SUCCEEDED','FAILED','REFUNDED')),
  constraint uq_payment_provider_payment_id unique (provider_payment_id),
  constraint pk_payment primary key (id)
);

-- foreign keys and indices
create index ix_app_user_avatar_document_id on app_user (avatar_document_id);
alter table app_user add constraint fk_app_user_avatar_document_id foreign key (avatar_document_id) references document (id) on delete set null on update restrict;

alter table character_background add constraint fk_character_background_character_id foreign key (character_id) references game_character (id) on delete cascade on update restrict;

create index ix_character_sheet_character_id on character_sheet (character_id);
alter table character_sheet add constraint fk_character_sheet_character_id foreign key (character_id) references game_character (id) on delete cascade on update restrict;

create index ix_character_sheet_document_id on character_sheet (document_id);
alter table character_sheet add constraint fk_character_sheet_document_id foreign key (document_id) references document (id) on delete restrict on update restrict;

create index ix_diary_character_id on diary (character_id);
alter table diary add constraint fk_diary_character_id foreign key (character_id) references game_character (id) on delete cascade on update restrict;

create index ix_diary_cover_document_id on diary (cover_document_id);
alter table diary add constraint fk_diary_cover_document_id foreign key (cover_document_id) references document (id) on delete set null on update restrict;

create index ix_diary_bookmark_page_id on diary (bookmark_page_id);
alter table diary add constraint fk_diary_bookmark_page_id foreign key (bookmark_page_id) references diary_page (id) on delete set null on update restrict;

create index ix_diary_page_diary_id on diary_page (diary_id);
alter table diary_page add constraint fk_diary_page_diary_id foreign key (diary_id) references diary (id) on delete cascade on update restrict;

create index ix_diary_page_tag_diary_id on diary_page_tag (diary_id);
alter table diary_page_tag add constraint fk_diary_page_tag_diary_id foreign key (diary_id) references diary (id) on delete cascade on update restrict;

alter table diary_page_tag add constraint fk_diary_page_tag_page_id foreign key (page_id) references diary_page (id) on delete cascade on update restrict;

create index ix_feedback_document_feedback_id on feedback_document (feedback_id);
alter table feedback_document add constraint fk_feedback_document_feedback_id foreign key (feedback_id) references feedback (id) on delete restrict on update restrict;

create index ix_feedback_document_document_id on feedback_document (document_id);
alter table feedback_document add constraint fk_feedback_document_document_id foreign key (document_id) references document (id) on delete restrict on update restrict;

create index ix_game_character_owner_id on game_character (owner_id);
alter table game_character add constraint fk_game_character_owner_id foreign key (owner_id) references app_user (id) on delete cascade on update restrict;

create index ix_game_character_avatar_document_id on game_character (avatar_document_id);
alter table game_character add constraint fk_game_character_avatar_document_id foreign key (avatar_document_id) references document (id) on delete set null on update restrict;

create index ix_payment_app_user_id on payment (app_user_id);
alter table payment add constraint fk_payment_app_user_id foreign key (app_user_id) references app_user (id) on delete set null on update restrict;

create index if not exists ix_app_user_email on app_user (email);
create index if not exists ix_character_sheet_title on character_sheet (title);
create index if not exists ix_character_sheet_game_system on character_sheet (game_system);
create index if not exists ix_diary_title on diary (title);
create index if not exists ix_document_bucket_object_key on document (bucket,object_key);
create index if not exists ix_document_name on document (name);
create index if not exists ix_feedback_type on feedback (type);
create index if not exists ix_feedback_status on feedback (status);
create index if not exists ix_feedback_email on feedback (email);
create index if not exists ix_game_character_name on game_character (name);
create index if not exists ix_game_character_campaign_name on game_character (campaign_name);
create index if not exists ix_payment_app_user_id_paid_at on payment (app_user_id,paid_at);
