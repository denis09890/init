package dev.init.classification;

/** Field of work a posting belongs to. */
public enum Domain {
	SOFTWARE,
	DATA,
	/** Non-software engineering: mechanical, electrical, civil, automotive, ... */
	ENGINEERING,
	DESIGN,
	PRODUCT,
	/** Finance, accounting, audit, banking. */
	FINANCE,
	MARKETING,
	SALES,
	HR,
	LEGAL,
	/** Operations, supply chain, logistics, procurement. */
	OPERATIONS,
	CUSTOMER_SUPPORT,
	RESEARCH,
	OTHER
}
