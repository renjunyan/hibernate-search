package org.hibernate.search.test.query.dsl;

import java.util.List;

import org.apache.lucene.search.Query;
import org.apache.solr.analysis.LowerCaseFilterFactory;
import org.apache.solr.analysis.NGramFilterFactory;
import org.apache.solr.analysis.SnowballPorterFilterFactory;
import org.apache.solr.analysis.StandardFilterFactory;
import org.apache.solr.analysis.StandardTokenizerFactory;
import org.apache.solr.analysis.StopFilterFactory;

import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;
import org.hibernate.search.Environment;
import org.hibernate.search.FullTextQuery;
import org.hibernate.search.FullTextSession;
import org.hibernate.search.Search;
import org.hibernate.search.annotations.Factory;
import org.hibernate.search.cfg.SearchMapping;
import org.hibernate.search.query.dsl.v2.QueryBuilder;
import org.hibernate.search.test.SearchTestCase;

/**
 * @author Emmanuel Bernard
 */
public class DSLTest extends SearchTestCase {

	public void testTermQueryOnAnalyzer() throws Exception {
		FullTextSession fts = initData();

		Transaction transaction = fts.beginTransaction();
		final QueryBuilder monthQb = fts.getSearchFactory()
				.buildQueryBuilder().forEntity( Month.class ).get();
		Query 
		//regular term query
		query = monthQb.term().on( "mythology" ).matches( "cold" ).createQuery();

		assertEquals( 0, fts.createFullTextQuery( query, Month.class ).getResultSize() );

		//term query based on several words
		query = monthQb.term().on( "mythology" ).matches( "colder darker" ).createQuery();

		assertEquals( 1, fts.createFullTextQuery( query, Month.class ).getResultSize() );

		//term query applying the analyzer and generating one term per word
		query = monthQb.term().on( "mythology_stem" ).matches( "snowboard" ).createQuery();

		assertEquals( 1, fts.createFullTextQuery( query, Month.class ).getResultSize() );

		//term query applying the analyzer and generating several terms per word
		query = monthQb.term().on( "mythology_ngram" ).matches( "snobored" ).createQuery();

		assertEquals( 1, fts.createFullTextQuery( query, Month.class ).getResultSize() );

		//term query not using analyzers
		query = monthQb.term().on( "mythology" ).matches( "Month" ).ignoreAnalyzer().createQuery();

		assertEquals( 0, fts.createFullTextQuery( query, Month.class ).getResultSize() );

		query = monthQb.term().on( "mythology" ).matches( "Month" ).createQuery();

		transaction.commit();

		cleanData( fts );
	}

	public void testFuzzyAndWildcardQuery() throws Exception {
		FullTextSession fts = initData();

		Transaction transaction = fts.beginTransaction();
		final QueryBuilder monthQb = fts.getSearchFactory()
				.buildQueryBuilder().forEntity( Month.class ).get();
		Query
		//fuzzy search with custom threshold and prefix
		query = monthQb
				.term().on( "mythology" ).matches( "calder" )
					.fuzzy()
						.threshold( .8f )
						.prefixLength( 1 )
				.createQuery();

		assertEquals( 1, fts.createFullTextQuery( query, Month.class ).getResultSize() );

		//wildcard query
		query = monthQb
				.term().on( "mythology" ).matches( "mon*" )
					.wildcard()
				.createQuery();
		System.out.println(query.toString(  ));
		assertEquals( 2, fts.createFullTextQuery( query, Month.class ).getResultSize() );

		transaction.commit();

		cleanData( fts );
	}

	public void testQueryCustomization() throws Exception {
		FullTextSession fts = initData();

		Transaction transaction = fts.beginTransaction();
		final QueryBuilder monthQb = fts.getSearchFactory()
				.buildQueryBuilder().forEntity( Month.class ).get();
		Query

		//combined query, January and february both contain whitening but February in a longer text
		query = monthQb
				.bool()
					.should( monthQb.term().on( "mythology" ).matches( "whitening" ).createQuery() )
					.should( monthQb.term().on( "history" ).matches( "whitening" ).createQuery() )
				.createQuery();

		List<Month> results = fts.createFullTextQuery( query, Month.class ).list();
		assertEquals( 2, results.size() );
		assertEquals( "January", results.get( 0 ).getName() );

		//boosted query, January and february both contain whitening but February in a longer text
		//since history is boosted, February should come first though
		query = monthQb
				.bool()
					.should( monthQb.term().on( "mythology" ).matches( "whitening" ).createQuery() )
					.should( monthQb.term().on( "history" ).matches( "whitening" ).boostedTo( 30 ).createQuery() )
				.createQuery();

		results = fts.createFullTextQuery( query, Month.class ).list();
		assertEquals( 2, results.size() );
		assertEquals( "February", results.get( 0 ).getName() );

		//FIXME add other method tests besides boostedTo

		transaction.commit();

		cleanData( fts );
	}

	//FIXME add boolean tests

	private void cleanData(FullTextSession fts) {
		Transaction tx = fts.beginTransaction();
		final List<Month> results = fts.createQuery( "from " + Month.class.getName() ).list();
		assertEquals( 2, results.size() );

		for (Month entity : results) {
			fts.delete( entity );
		}
		tx.commit();
		fts.close();
	}

	private FullTextSession initData() {
		Session session = openSession();
		FullTextSession fts = Search.getFullTextSession( session );
		Transaction tx = fts.beginTransaction();
		fts.persist( new Month("January", "Month of colder and whitening", "Historically colder than any other month in the northern hemisphere") );
		fts.persist( new Month("February", "Month of snowboarding", "Historically, the month where we make babies while watching the whitening landscape") );
		tx.commit();
		fts.clear();
		return fts;
	}

	@Override
	protected Class<?>[] getMappings() {
		return new Class<?>[] {
				Month.class
		};
	}

	@Override
	protected void configure(Configuration cfg) {
		super.configure( cfg );
		cfg.getProperties().put( Environment.MODEL_MAPPING, MappingFactory.class.getName() );
	}

	public static class MappingFactory {
		@Factory
			public SearchMapping build() {
				SearchMapping mapping = new SearchMapping();
			mapping
				.analyzerDef( "stemmer", StandardTokenizerFactory.class )
					.filter( StandardFilterFactory.class )
					.filter( LowerCaseFilterFactory.class )
					.filter( StopFilterFactory.class )
					.filter( SnowballPorterFilterFactory.class )
						.param( "language", "English" )
				.analyzerDef( "ngram", StandardTokenizerFactory.class )
					.filter( StandardFilterFactory.class )
					.filter( LowerCaseFilterFactory.class )
					.filter( StopFilterFactory.class )
					.filter( NGramFilterFactory.class )
						.param( "minGramSize", "3" )
						.param( "maxGramSize", "3" );
			return mapping;
		}
	}
}
