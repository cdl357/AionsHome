-- 顶部情话（回家页可编辑文案）——在你们的 Supabase 项目里执行一次
create table if not exists home_quote (
  id int primary key default 1,
  content text not null,
  updated_at timestamptz default now()
);

insert into home_quote (id, content) values (1, '今天的星星比昨天多了一颗。')
  on conflict (id) do nothing;

-- App 端读取/更新走 REST：
--   读: GET  {URL}/rest/v1/home_quote?id=eq.1&select=content
--   改: PUT  {URL}/rest/v1/home_quote?on_conflict=id   body: {"id":1,"content":"..."}
