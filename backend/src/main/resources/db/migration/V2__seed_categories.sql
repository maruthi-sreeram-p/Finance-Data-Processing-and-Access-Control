-- ---------------------------------------------------------------------------
-- V2: reference categories.
--
-- Seeded through a migration rather than a CommandLineRunner so that every
-- environment -- dev, test and production -- starts from the same, versioned set,
-- and so the insert is not re-run on every application boot.
--
-- `type` drives the income/expense validation rule. Investment and Other are BOTH:
-- an investment can be a purchase or a payout, and Other is the deliberate catch-all.
-- ---------------------------------------------------------------------------

INSERT INTO categories (name, description, type, created_at, updated_at) VALUES
    ('Food',          'Groceries, restaurants and takeaway',            'EXPENSE', NOW(6), NOW(6)),
    ('Transport',     'Fuel, public transport, ride hailing, parking',  'EXPENSE', NOW(6), NOW(6)),
    ('Rent',          'Rent, mortgage and housing charges',             'EXPENSE', NOW(6), NOW(6)),
    ('Education',     'Tuition, courses, books and certifications',     'EXPENSE', NOW(6), NOW(6)),
    ('Shopping',      'Clothing, electronics and general retail',       'EXPENSE', NOW(6), NOW(6)),
    ('Entertainment', 'Streaming, events, games and hobbies',           'EXPENSE', NOW(6), NOW(6)),
    ('Healthcare',    'Consultations, medication and insurance',        'EXPENSE', NOW(6), NOW(6)),
    ('Bills',         'Utilities, internet, phone and subscriptions',   'EXPENSE', NOW(6), NOW(6)),
    ('Salary',        'Employment income',                              'INCOME',  NOW(6), NOW(6)),
    ('Freelancing',   'Contract and freelance income',                  'INCOME',  NOW(6), NOW(6)),
    ('Investment',    'Investment purchases and returns',               'BOTH',    NOW(6), NOW(6)),
    ('Other',         'Anything that does not fit another category',    'BOTH',    NOW(6), NOW(6));
